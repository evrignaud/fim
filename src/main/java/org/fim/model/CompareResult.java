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

import org.fim.util.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.atteo.evo.inflector.English.plural;
import static org.fim.util.FormatUtil.formatCreationTime;
import static org.fim.util.FormatUtil.formatDate;
import static org.fim.util.FormatUtil.formatLastModified;

public class CompareResult {
    public static final String NOTHING = "[nothing]";

    private static final Comparator<Difference> FILE_NAME_COMPARATOR = new Difference.FileNameComparator();

    private final List<Difference> added;
    private final List<Difference> copied;
    private final List<Difference> duplicated;
    private final List<Difference> dateModified;
    private final List<Difference> contentModified;
    private final List<Difference> attributesModified;
    private final List<Difference> renamed;
    private final List<Difference> deleted;
    private final List<Difference> corrupted;

    private final Context context;
    private final State lastState;

    public CompareResult(Context context, State lastState) {
        this(context, lastState, null);
    }

    public CompareResult(Context context, State lastState, State currentState) {
        this.context = context;
        this.lastState = lastState;

        added = buildModifications(currentState, Modification.added);
        copied = buildModifications(currentState, Modification.copied);
        duplicated = buildModifications(currentState, Modification.duplicated);
        dateModified = buildModifications(currentState, Modification.dateModified);
        contentModified = buildModifications(currentState, Modification.contentModified);
        attributesModified = buildModifications(currentState, Modification.attributesModified);
        renamed = buildModifications(currentState, Modification.renamed);
        deleted = buildModifications(currentState, Modification.deleted);
        corrupted = buildModifications(currentState, Modification.corrupted);
    }

    private List<Difference> buildModifications(State state, Modification modification) {
        List<Difference> differences;
        if (state != null) {
            differences = state.getFileStates().stream()
                    .filter(fileState -> fileState.getModification() == modification)
                    .map(Difference::new)
                    .collect(Collectors.toList());
        } else {
            differences = new ArrayList<>();
        }
        return differences;
    }

    public void sortResults() {
        sortDifferences(added);
        sortDifferences(copied);
        sortDifferences(duplicated);
        sortDifferences(dateModified);
        sortDifferences(contentModified);
        sortDifferences(attributesModified);
        sortDifferences(renamed);
        sortDifferences(deleted);
        sortDifferences(corrupted);
    }

    private void sortDifferences(List<Difference> differences) {
        differences.sort(FILE_NAME_COMPARATOR);
    }

    public CompareResult displayChanges() {
        return displayChanges(null);
    }

    public CompareResult displayChanges(String notModifiedMessage) {
        if (lastState != null) {
            Logger.out.printf("Comparing with the last committed state from %s%n", formatDate(lastState.getTimestamp()));
            if (!lastState.getComment().isEmpty()) {
                Logger.out.println("Comment: " + lastState.getComment());
            }
            Logger.newLine();
        }

        if (context.isVerbose() && somethingModified()) {
            String stateFormat = "%-17s ";

            final String addedStr = String.format(stateFormat, "Added:");
            displayDifferences(context, addedStr, added,
                    diff -> Logger.out.printf(addedStr + "%s%n", diff.getFileState().getFileName()));

            final String copiedStr = String.format(stateFormat, "Copied:");
            displayDifferences(context, copiedStr, copied,
                    diff -> Logger.out.printf(copiedStr + "%s \t(was %s)%n", diff.getFileState().getFileName(), getPreviousFileName(diff)));

            final String duplicatedStr = String.format(stateFormat, "Duplicated:");
            displayDifferences(context, duplicatedStr, duplicated,
                    diff -> Logger.out.printf(duplicatedStr + "%s = %s%s%n", diff.getFileState().getFileName(), getPreviousFileName(diff),
                            formatModifiedAttributesWithoutTimeChange(diff, true)));

            final String dateModifiedStr = String.format(stateFormat, "Date modified:");
            displayDifferences(context, dateModifiedStr, dateModified,
                    diff -> Logger.out.printf(dateModifiedStr + "%s \t%s%n", diff.getFileState().getFileName(),
                            formatModifiedAttributes(diff, false)));

            final String contentModifiedStr = String.format(stateFormat, "Content modified:");
            displayDifferences(context, contentModifiedStr, contentModified,
                    diff -> Logger.out.printf(contentModifiedStr + "%s \t%s%n", diff.getFileState().getFileName(),
                            formatModifiedAttributes(diff, false)));

            final String attrsModifiedStr = String.format(stateFormat, "Attrs. modified:");
            displayDifferences(context, attrsModifiedStr, attributesModified,
                    diff -> Logger.out.printf(attrsModifiedStr + "%s \t%s%n", diff.getFileState().getFileName(),
                            formatModifiedAttributes(diff, false)));

            final String renamedStr = String.format(stateFormat, "Renamed:");
            displayDifferences(context, renamedStr, renamed,
                    diff -> Logger.out.printf(renamedStr + "%s -> %s%s%n", getPreviousFileName(diff), diff.getFileState().getFileName(),
                            formatModifiedAttributesWithoutTimeChange(diff, true)));

            final String deletedStr = String.format(stateFormat, "Deleted:");
            displayDifferences(context, deletedStr, deleted,
                    diff -> Logger.out.printf(deletedStr + "%s%n", diff.getFileState().getFileName()));

            final String corruptedStr = String.format(stateFormat, "Corrupted?:");
            displayDifferences(context, corruptedStr, corrupted,
                    diff -> Logger.out.printf(corruptedStr + "%s \t%s%n", diff.getFileState().getFileName(), formatModifiedAttributes(diff, false)));

            Logger.newLine();
        }

        displayCounts(notModifiedMessage);

        return this;
    }

    private static String getPreviousFileName(Difference diff) {
        if (diff.getPreviousFileState() == null) { // This case happens when displaying log for States produced using Fim before 1.2.1
            return "?";
        }
        return diff.getPreviousFileState().getFileName();
    }

    static void displayDifferences(Context context, String actionStr,
            List<Difference> differences, Consumer<Difference> displayOneDifference) {
        int truncateOutput = context.getTruncateOutput();
        if (truncateOutput < 1) {
            return;
        }

        int quarter = truncateOutput / 4;

        int differencesSize = differences.size();
        for (int index = 0; index < differencesSize; index++) {
            Difference difference = differences.get(index);
            if (index >= truncateOutput && (differencesSize - index) > quarter) {
                Logger.out.println("  [Too many lines. Truncating the output] ...");
                int moreFiles = differencesSize - index;
                Logger.out.printf("%s%d %s more%n", actionStr, moreFiles, plural("file", moreFiles));
                break;
            }

            if (displayOneDifference != null) {
                displayOneDifference.accept(difference);
            }
        }
    }

    static String formatModifiedAttributes(Difference diff, boolean nextLine) {
        return internalFormatModifiedAttributes(diff, nextLine, true);
    }

    private static String formatModifiedAttributesWithoutTimeChange(Difference diff, boolean nextLine) {
        return internalFormatModifiedAttributes(diff, nextLine, false);
    }

    private static String internalFormatModifiedAttributes(Difference diff, boolean nextLine, boolean displayTimeChange) {
        if (diff.getPreviousFileState() == null) { // This case happens when displaying log for States produced using Fim before 1.2.1
            return nextLine ? " ?" : "?";
        }

        int modifCount = 0;
        StringBuilder modification = new StringBuilder(nextLine ? " " : ""); // Put a white space to force to add a separator

        Map<String, String> previousFileAttributes = diff.getPreviousFileState().getFileAttributes();
        Map<String, String> currentFileAttributes = diff.getFileState().getFileAttributes();
        for (FileAttribute attribute : FileAttribute.values()) {
            String key = attribute.name();
            String previousValue = getValue(previousFileAttributes, key);
            String currentValue = getValue(currentFileAttributes, key);

            if (!Objects.equals(previousValue, currentValue)) {
                modifCount++;
                addSeparator(diff, modification);
                modification.append(key).append(": ").append(previousValue).append(" -> ").append(currentValue);
            }
        }

        if (displayTimeChange) {
            modifCount = +formatTimeChange(diff, modification);
        }

        if (modifCount > 1) {
            modification.append('\n');
        }

        return modification.toString();
    }

    private static int formatTimeChange(Difference diff, StringBuilder modification) {
        int modifCount = 0;
        boolean creationTimeChanged = diff.isCreationTimeChanged();
        boolean lastModifiedChanged = diff.isLastModifiedChanged();

        FileTime fileTime = diff.getFileState().getFileTime();
        FileTime previousFileTime = diff.getPreviousFileState().getFileTime();

        if (creationTimeChanged && lastModifiedChanged &&
            fileTime.getCreationTime() == fileTime.getLastModified() && previousFileTime.getCreationTime() == previousFileTime.getLastModified()) {
            modifCount++;
            addSeparator(diff, modification);
            modification.append("last modified: ")
                    .append(formatLastModified(diff.getPreviousFileState()))
                    .append(" -> ")
                    .append(formatLastModified(diff.getFileState()));
        } else {
            if (creationTimeChanged) {
                modifCount++;
                addSeparator(diff, modification);
                modification.append("creation time: ")
                        .append(formatCreationTime(diff.getPreviousFileState()))
                        .append(" -> ")
                        .append(formatCreationTime(diff.getFileState()));
            }

            if (lastModifiedChanged) {
                modifCount++;
                addSeparator(diff, modification);
                modification.append("last modified: ")
                        .append(formatLastModified(diff.getPreviousFileState()))
                        .append(" -> ")
                        .append(formatLastModified(diff.getFileState()));
            }
        }
        return modifCount;
    }

    static void addSeparator(Difference diff, StringBuilder modification) {
        if (modification.isEmpty()) {
            return;
        }

        modification.append("\n");
        int len = 17 + 1 + diff.getFileState().getFileName().length() + 1;
        modification.append(" ".repeat(Math.max(0, len)));
        modification.append('\t');
    }

    static String getValue(Map<String, String> attributes, String key) {
        String value = attributes != null ? attributes.get(key) : null;
        if (value == null || value.isEmpty()) {
            value = NOTHING;
        }
        return value;
    }

    private CompareResult displayCounts(String notModifiedMessage) {
        if (somethingModified()) {
            String message = "";
            if (!added.isEmpty()) {
                message += String.format("%d added, ", added.size());
            }

            if (!copied.isEmpty()) {
                message += String.format("%d copied, ", copied.size());
            }

            if (!duplicated.isEmpty()) {
                message += String.format("%d duplicated, ", duplicated.size());
            }

            if (!dateModified.isEmpty()) {
                message += String.format("%d date modified, ", dateModified.size());
            }

            if (!attributesModified.isEmpty()) {
                message += String.format("%d attrs. modified, ", attributesModified.size());
            }

            if (!contentModified.isEmpty()) {
                message += String.format("%d content modified, ", contentModified.size());
            }

            if (!renamed.isEmpty()) {
                message += String.format("%d renamed, ", renamed.size());
            }

            if (!deleted.isEmpty()) {
                message += String.format("%d deleted, ", deleted.size());
            }

            if (!corrupted.isEmpty()) {
                message += String.format("%d corrupted, ", corrupted.size());
            }

            message = message.replaceAll(", $", "");
            Logger.out.println(message + addExpectIgnored());
        } else if (notModifiedMessage != null) {
            Logger.out.println(notModifiedMessage + addExpectIgnored());
        }

        return this;
    }

    private String addExpectIgnored() {
        Ignored ignored = context.getIgnored();
        if (!ignored.somethingIgnored()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" (Not taking in account ");
        if (ignored.isAttributesIgnored()) {
            sb.append("file attributes, ");
        }
        if (ignored.isDatesIgnored()) {
            sb.append("modification dates, ");
        }
        if (ignored.isRenamedIgnored()) {
            sb.append("renamed files, ");
        }
        String result = sb.toString();
        result = result.substring(0, result.length() - 2);
        result += ")";
        return result;
    }

    public boolean somethingModified() {
        return modifiedCount() > 0;
    }

    public int modifiedCount() {
        return added.size() + copied.size() + duplicated.size() + dateModified.size() + contentModified.size() +
               attributesModified.size() + renamed.size() + deleted.size() + corrupted.size();
    }

    public ModificationCounts getModificationCounts() {
        ModificationCounts modificationCounts = new ModificationCounts();
        modificationCounts.setAdded(added.size());
        modificationCounts.setCopied(copied.size());
        modificationCounts.setDuplicated(duplicated.size());
        modificationCounts.setDateModified(dateModified.size());
        modificationCounts.setContentModified(contentModified.size());
        modificationCounts.setAttributesModified(attributesModified.size());
        modificationCounts.setRenamed(renamed.size());
        modificationCounts.setDeleted(deleted.size());

        return modificationCounts;
    }

    public List<Difference> getAdded() {
        return added;
    }

    public List<Difference> getCopied() {
        return copied;
    }

    public List<Difference> getDuplicated() {
        return duplicated;
    }

    public List<Difference> getDateModified() {
        return dateModified;
    }

    public List<Difference> getContentModified() {
        return contentModified;
    }

    public List<Difference> getAttributesModified() {
        return attributesModified;
    }

    public List<Difference> getRenamed() {
        return renamed;
    }

    public List<Difference> getDeleted() {
        return deleted;
    }

    public List<Difference> getCorrupted() {
        return corrupted;
    }
}

