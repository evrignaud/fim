/*
 * This file is part of Fim - File Integrity Manager
 *
 * Copyright (C) 2026 Etienne Vrignaud
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

package org.fim.command;

import org.fim.command.exception.DontWantToContinueException;
import org.fim.command.exception.RepositoryException;
import org.fim.internal.InitProgressManager;
import org.fim.internal.SettingsManager;
import org.fim.internal.StateComparator;
import org.fim.internal.StateGenerator;
import org.fim.internal.StateManager;
import org.fim.model.CompareResult;
import org.fim.model.Context;
import org.fim.model.State;
import org.fim.util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.model.HashMode.hashAll;
import static org.fim.util.HashModeUtil.hashModeToString;

public class InitCommand extends AbstractCommand {
    @Override
    public String getCmdName() {
        return "init";
    }

    @Override
    public String getShortCmdName() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Initialize a Fim repository and create the first State";
    }

    @Override
    public FimReposConstraint getFimReposConstraint() {
        return FimReposConstraint.MUST_NOT_EXIST;
    }

    @Override
    public Object execute(Context context) throws Exception {
        if (context.getComment().length() == 0) {
            Logger.out.println("No comment provided. You are going to initialize your repository using the default comment.");
            if (!confirmAction(context, "continue")) {
                throw new DontWantToContinueException();
            }
        }

        String comment = context.getComment();
        if (comment.length() == 0) {
            comment = "Initial State";
        }

        boolean dotFimDirCreated = createDotFimDir(context);
        InitProgressManager initProgressManager = new InitProgressManager(context);
        Thread interruptionHandler = null;
        try {
            resumePreviousProgress(initProgressManager);
            interruptionHandler = addInterruptionHandler(initProgressManager);

            StateGenerator stateGenerator = new StateGenerator(context);
            stateGenerator.setInitProgressManager(initProgressManager);
            State currentState = stateGenerator.generateState(comment, context.getCurrentDirectory(), context.getCurrentDirectory());

            CompareResult result = new StateComparator(context, null, currentState).compare();
            currentState.setModificationCounts(result.getModificationCounts());

            createRepository(context);

            new StateManager(context).createNewState(currentState);

            // The first State is committed, so there is no longer an unfinished init to resume
            initProgressManager.delete();

            result.displayChanges();
            Logger.out.println("Repository initialized");

            return currentState;
        } catch (Exception ex) {
            discardUnusableRepository(context, dotFimDirCreated, initProgressManager);
            throw ex;
        } finally {
            removeInterruptionHandler(interruptionHandler);
        }
    }

    /**
     * Read back the hashes an interrupted init left behind, so that they do not have to be computed again.
     */
    private void resumePreviousProgress(InitProgressManager initProgressManager) {
        if (!initProgressManager.exists()) {
            return;
        }

        int reusableFileCount = initProgressManager.load();
        if (reusableFileCount > 0) {
            Logger.info(String.format("Resuming the interrupted 'fim init'. The hash of %d %s is already known",
                    reusableFileCount, plural("file", reusableFileCount)));
        }
    }

    /**
     * Arrange for the progress to be saved when the init is stopped, which is the whole point for the user:
     * pressing Ctrl-C is how an init that takes too long gets paused.
     * <p>
     * This only shortens what a stop gives up. The progress is saved regularly while the init runs, so a
     * stop the handler cannot serve, a machine losing power for instance, still resumes from the last save.
     */
    private Thread addInterruptionHandler(InitProgressManager initProgressManager) {
        Thread interruptionHandler = new Thread(initProgressManager::saveOnInterruption, "fim-init-interruption");
        Runtime.getRuntime().addShutdownHook(interruptionHandler);
        return interruptionHandler;
    }

    private void removeInterruptionHandler(Thread interruptionHandler) {
        if (interruptionHandler == null) {
            return;
        }

        try {
            Runtime.getRuntime().removeShutdownHook(interruptionHandler);
        } catch (IllegalStateException ex) {
            // The JVM is already shutting down, so the handler is running or has run. Nothing to remove.
        }
    }

    /**
     * Create the directory holding the Fim repository.
     * <p>
     * It has to exist before the hashing starts, because that is where the progress of the init is saved.
     * The States directory is still created only once the hashing succeeded, so an init that failed leaves
     * nothing that the other commands would mistake for a usable repository.
     *
     * @return true when this run created the directory
     */
    private boolean createDotFimDir(Context context) {
        Path dotFimDir = context.getRepositoryDotFimDir();
        if (Files.exists(dotFimDir)) {
            return false;
        }

        try {
            Files.createDirectories(dotFimDir);
            return true;
        } catch (IOException ex) {
            Logger.error(String.format("Not able to create the '%s' directory that holds the Fim repository", dotFimDir), ex,
                    context.isDisplayStackTrace());
            throw new RepositoryException();
        }
    }

    /**
     * Remove the directory this run created when the init failed without leaving anything to resume from.
     * An empty '.fim' directory would otherwise turn every later 'fim init' down while never being of any
     * use to the other commands.
     */
    private void discardUnusableRepository(Context context, boolean dotFimDirCreated, InitProgressManager initProgressManager) {
        if (!dotFimDirCreated || initProgressManager.exists() || Files.exists(context.getRepositoryStatesDir())) {
            return;
        }

        Path dotFimDir = context.getRepositoryDotFimDir();
        try {
            Files.deleteIfExists(dotFimDir);
        } catch (IOException ex) {
            Logger.rawDebug(String.format("Not able to remove the '%s' directory: %s", dotFimDir, ex.getMessage()));
        }
    }

    private void createRepository(Context context) {
        try {
            Files.createDirectories(context.getRepositoryStatesDir());
        } catch (IOException ex) {
            Logger.error(String.format("Not able to create the '%s' directory that holds the Fim repository", context.getRepositoryDotFimDir()), ex,
                    context.isDisplayStackTrace());
            throw new RepositoryException();
        }

        if (context.getHashMode() != hashAll) {
            SettingsManager settingsManager = new SettingsManager(context);
            settingsManager.setGlobalHashMode(context.getHashMode());
            settingsManager.save();

            Logger.warning(String.format("Global hash mode set to '%s'%n", hashModeToString(context.getHashMode())));
        }
    }
}
