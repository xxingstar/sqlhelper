/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the LGPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.gnu.org/licenses/lgpl-3.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jn.sqlhelper.langx.configuration.file.directoryfile;

import com.jn.langx.lifecycle.InitializationException;
import com.jn.langx.util.Emptys;
import com.jn.langx.util.Preconditions;
import com.jn.langx.util.Strings;
import com.jn.langx.util.collection.Collects;
import com.jn.langx.util.collection.diff.MapDiffResult;
import com.jn.langx.util.concurrent.CommonThreadFactory;
import com.jn.langx.util.function.Consumer2;
import com.jn.langx.util.function.Supplier;
import com.jn.langx.util.io.file.FileFilter;
import com.jn.langx.util.io.file.Files;
import com.jn.langx.util.timing.timer.HashedWheelTimer;
import com.jn.langx.util.timing.timer.Timeout;
import com.jn.langx.util.timing.timer.Timer;
import com.jn.langx.util.timing.timer.TimerTask;
import com.jn.sqlhelper.langx.configuration.AbstractConfigurationRepository;
import com.jn.sqlhelper.langx.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * multiple configuration file in one directory, every configuration will be load as a configuration
 *
 * @param <T>
 */
public class DirectoryBasedFileConfigurationRepository<T extends Configuration> extends AbstractConfigurationRepository<T, DirectoryBasedFileConfigurationLoader<T>, DirectoryBasedFileConfigurationWriter<T>> {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryBasedFileConfigurationRepository.class);
    /**
     * configuration file filter
     */
    private List<FileFilter> fileFilters;

    /**
     * units: seconds
     * scan interval, if <=0, will not refresh
     */
    private int refreshIntervalInSeconds = -1;

    private String directory;

    private Map<String, Long> lastModifiedTimeMap = Collects.emptyHashMap();

    private volatile boolean inited;

    private Timer timer;

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    private Supplier<String, String> configurationIdSupplier = new Supplier<String, String>() {
        @Override
        public String get(String filename) {
            return filename;
        }
    };
    private Supplier<String, String> filenameSupplier = new Supplier<String, String>() {
        @Override
        public String get(String configurationId) {
            return configurationId;
        }
    };

    public void setRefreshIntervalInSeconds(int refreshIntervalInSeconds) {
        this.refreshIntervalInSeconds = refreshIntervalInSeconds;
    }

    public void setConfigurationIdSupplier(Supplier<String, String> configurationIdSupplier) {
        this.configurationIdSupplier = configurationIdSupplier;
    }

    public void setFilenameSupplier(Supplier<String, String> filenameSupplier) {
        this.filenameSupplier = filenameSupplier;
    }

    @Override
    public void init() throws InitializationException {
        if (!inited) {
            super.init();
            Preconditions.checkNotNull(loader, "the configuration load is null");
            Preconditions.checkTrue(Strings.isNotBlank(directory), "directory is null");
            if (!Files.exists(new File(directory))) {
                logger.warn("Can't find a directory : {}, will create it", new File(directory).getAbsoluteFile());
                Files.makeDirs(directory);
            }
            loader.setDirectory(directory);

            if (this.writer == null) {
                logger.warn("The writer is not specified for the repository ({}), will disable write configuration to storage", name);
            }
            // enable refresh
            if (refreshIntervalInSeconds > 0) {
                if (timer == null) {
                    logger.warn("The timer is not specified for the repository ({}) , will use a simple timer", name);
                    timer = new HashedWheelTimer(new CommonThreadFactory("Configuration", true), 50, TimeUnit.MILLISECONDS);
                }
            } else {
                logger.info("The configuration refresh task is disabled for repository: {}", name);
            }
            inited = true;

        }
    }

    @Override
    public void startup() {
        if (!running) {
            super.startup();
            if (refreshIntervalInSeconds > 0) {
                timer.newTimeout(new TimerTask() {
                    @Override
                    public void run(Timeout timeout) throws Exception {
                        try {
                            reload();
                        } catch (Throwable ex) {
                            logger.error(ex.getMessage(), ex);
                        } finally {
                            if (running) {
                                timer.newTimeout(this, refreshIntervalInSeconds, TimeUnit.SECONDS);
                            }
                        }
                    }
                }, refreshIntervalInSeconds, TimeUnit.SECONDS);
            } else {
                reload();
            }
        }
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    private void reload() {
        Map<String, Long> modifiedTimeMap = loader.scanConfigurationFileModifiedTimes();
        try {
            MapDiffResult<String, Long> lastModifiedDiffResult = Collects.diff(lastModifiedTimeMap, modifiedTimeMap);
            Collects.forEach(lastModifiedDiffResult.getRemoves(), new Consumer2<String, Long>() {
                @Override
                public void accept(String id, Long lastModified) {
                    T configuration = removeById(id);
                    if (Emptys.isNotNull(configuration)) {
                        // TODO publish event
                    }
                }
            });
            Collects.forEach(lastModifiedDiffResult.getUpdates(), new Consumer2<String, Long>() {
                @Override
                public void accept(String id, Long lastModified) {
                    T configuration = loader.load(id);
                    update(configuration);
                    // TODO publish event
                }
            });
            Collects.forEach(lastModifiedDiffResult.getAdds(), new Consumer2<String, Long>() {
                @Override
                public void accept(String id, Long lastModified) {
                    T configuration = loader.load(id);
                    add(configuration);
                    // TODO publish event
                }
            });
        } finally {
            lastModifiedTimeMap = modifiedTimeMap;
        }
    }

}