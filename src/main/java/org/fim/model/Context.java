/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2017  Etienne Vrignaud
 *
 * Fim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fim.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.fim.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.fim.model.HashMode.hashAll;

public class Context {
    public static final String DOT_FIM_DIR = ".fim";

    private boolean invokedFromSubDirectory;
    private Path currentDirectory;
    private Path repositoryRootDir;
    private boolean verbose;
    private HashMode hashMode;
    private String comment;
    private boolean useLastState;
    private int threadCount;
    private boolean threadCountSpecified;
    private String masterFimRepositoryDir;
    private boolean alwaysYes;
    private boolean displayStackTrace;
    private int truncateOutput;
    private boolean purgeStates;
    private Ignored ignored;
    private boolean removeDuplicates;
    private boolean calledFromTest;
    private boolean dynamicScaling;
    private boolean sortAscending;
    private SortMethod sortMethod;
    private List<FilePattern> includePatterns;
    private List<FilePattern> excludePatterns;
    private OutputType outputType;

    public Context() {
        setInvokedFromSubDirectory(false);
        setCurrentDirectory(Paths.get("."));
        setRepositoryRootDir(getCurrentDirectory());
        setVerbose(true);
        setHashMode(hashAll);
        setComment("");
        setUseLastState(false);
        setThreadCount(-1);
        setThreadCountSpecified(false);
        setDynamicScaling(true);
        setMasterFimRepositoryDir(null);
        setAlwaysYes(false);
        setTruncateOutput(200);
        setIgnored(new Ignored());
        setRemoveDuplicates(false);
        setCalledFromTest(false);
        setSortAscending(false);
        setSortMethod(SortMethod.wasted);
        setOutputType(OutputType.human);
    }

    public boolean isInvokedFromSubDirectory() {
        return invokedFromSubDirectory;
    }

    public void setInvokedFromSubDirectory(boolean invokedFromSubDirectory) {
        this.invokedFromSubDirectory = invokedFromSubDirectory;
    }

    public Path getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(Path currentDirectory) {
        this.currentDirectory = currentDirectory;
    }

    public Path getAbsoluteCurrentDirectory() {
        return currentDirectory.toAbsolutePath().normalize();
    }

    public Path getRepositoryRootDir() {
        return repositoryRootDir;
    }

    public void setRepositoryRootDir(Path repositoryRootDir) {
        this.repositoryRootDir = repositoryRootDir.toAbsolutePath().normalize();
    }

    public Path getRepositoryDotFimDir() {
        return repositoryRootDir.resolve(DOT_FIM_DIR);
    }

    public Path getRepositoryStatesDir() {
        return getRepositoryDotFimDir().resolve("states");
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public HashMode getHashMode() {
        return hashMode;
    }

    public void setHashMode(HashMode hashMode) {
        this.hashMode = hashMode;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isUseLastState() {
        return useLastState;
    }

    public void setUseLastState(boolean useLastState) {
        this.useLastState = useLastState;
    }

    public String getMasterFimRepositoryDir() {
        return masterFimRepositoryDir;
    }

    public void setMasterFimRepositoryDir(String masterFimRepositoryDir) {
        this.masterFimRepositoryDir = masterFimRepositoryDir;
    }

    public boolean isAlwaysYes() {
        return alwaysYes;
    }

    public void setAlwaysYes(boolean alwaysYes) {
        this.alwaysYes = alwaysYes;
    }

    public boolean isDisplayStackTrace() {
        return displayStackTrace;
    }

    public void setDisplayStackTrace(boolean displayStackTrace) {
        this.displayStackTrace = displayStackTrace;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isThreadCountSpecified() {
        return threadCountSpecified;
    }

    public void setThreadCountSpecified(boolean threadCountSpecified) {
        this.threadCountSpecified = threadCountSpecified;
    }

    public void setDynamicScaling(boolean dynamicScaling) {
        this.dynamicScaling = dynamicScaling;
    }

    public boolean isDynamicScaling() {
        return dynamicScaling;
    }

    public void setTruncateOutput(int truncateOutput) {
        this.truncateOutput = truncateOutput;
    }

    public int getTruncateOutput() {
        return truncateOutput;
    }

    public void setPurgeStates(boolean purgeStates) {
        this.purgeStates = purgeStates;
    }

    public boolean isPurgeStates() {
        return purgeStates;
    }

    public Ignored getIgnored() {
        return ignored;
    }

    public void setIgnored(Ignored ignored) {
        this.ignored = ignored;
    }

    public boolean isRemoveDuplicates() {
        return removeDuplicates;
    }

    public void setRemoveDuplicates(boolean removeDuplicates) {
        this.removeDuplicates = removeDuplicates;
    }

    public boolean isCalledFromTest() {
        return calledFromTest;
    }

    public void setCalledFromTest(boolean calledFromTest) {
        this.calledFromTest = calledFromTest;
    }

    public boolean isSortAscending() {
        return sortAscending;
    }

    public void setSortAscending(boolean sortAscending) {
        this.sortAscending = sortAscending;
    }

    public SortMethod getSortMethod() {
        return sortMethod;
    }

    public void setSortMethod(SortMethod sortMethod) {
        this.sortMethod = sortMethod;
    }

    public void setIncludePatterns(List<FilePattern> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<FilePattern> getIncludePatterns() {
        return includePatterns;
    }

    public void setExcludePatterns(List<FilePattern> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public List<FilePattern> getExcludePatterns() {
        return excludePatterns;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    @Override
    public Context clone() {
        Context cloned = new Context();
        cloned.invokedFromSubDirectory = this.invokedFromSubDirectory;
        cloned.currentDirectory = this.currentDirectory;
        cloned.repositoryRootDir = this.repositoryRootDir;
        cloned.verbose = this.verbose;
        cloned.hashMode = this.hashMode;
        cloned.comment = this.comment;
        cloned.useLastState = this.useLastState;
        cloned.threadCount = this.threadCount;
        cloned.threadCountSpecified = this.threadCountSpecified;
        cloned.masterFimRepositoryDir = this.masterFimRepositoryDir;
        cloned.alwaysYes = this.alwaysYes;
        cloned.displayStackTrace = this.displayStackTrace;
        cloned.truncateOutput = this.truncateOutput;
        cloned.purgeStates = this.purgeStates;
        cloned.ignored = this.ignored.clone();
        cloned.removeDuplicates = this.removeDuplicates;
        cloned.calledFromTest = this.calledFromTest;
        cloned.dynamicScaling = this.dynamicScaling;
        cloned.sortAscending = this.sortAscending;
        cloned.sortMethod = this.sortMethod;

        cloned.includePatterns = null;
        if (this.includePatterns != null) {
            cloned.includePatterns = new ArrayList<>();
            for (FilePattern pattern : this.includePatterns) {
                cloned.includePatterns.add(pattern.clone());
            }
        }

        cloned.excludePatterns = null;
        if (this.excludePatterns != null) {
            cloned.excludePatterns = new ArrayList<>();
            for (FilePattern pattern : this.excludePatterns) {
                cloned.excludePatterns.add(pattern.clone());
            }
        }

        cloned.outputType = this.outputType;
        return cloned;
    }
}
