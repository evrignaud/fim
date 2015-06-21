import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class DuplicateFinder
{
	private final boolean verbose;
	private long duplicatesCount;
	private long duplicatedFilesCount;
	private List<FileState> duplicates;
	private Comparator<FileState> hashComparator;

	public DuplicateFinder(boolean verbose)
	{
		this.verbose = verbose;
		this.hashComparator = new HashComparator();
	}

	public void findDuplicates(State state)
	{
		List<FileState> fileStates = new ArrayList<>(state.getFileStates());
		Collections.sort(fileStates, hashComparator);

		duplicatesCount = 0;
		duplicatedFilesCount = 0;
		duplicates = new ArrayList<>();
		String previousHash = "";
		for (FileState fileState : fileStates)
		{
			if (!previousHash.equals(fileState.getHash()))
			{
				takeInAccountDuplicates();
				duplicates.clear();
			}

			previousHash = fileState.getHash();
			duplicates.add(fileState);
		}
		takeInAccountDuplicates();

		System.out.println(duplicatedFilesCount + " duplicated files");
	}

	private void takeInAccountDuplicates()
	{
		if (duplicates.size() > 1)
		{
			duplicatesCount++;
			duplicatedFilesCount += duplicates.size() - 1;

			if (verbose)
			{
				System.out.println("Duplicate #" + duplicatesCount);
				for (FileState fs : duplicates)
				{
					System.out.println("  " + fs.getFileName());
				}
				System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
				System.out.println("");
			}
		}
	}

	public long getDuplicatesCount()
	{
		return duplicatesCount;
	}

	public void setDuplicatesCount(long duplicatesCount)
	{
		this.duplicatesCount = duplicatesCount;
	}

	public long getDuplicatedFilesCount()
	{
		return duplicatedFilesCount;
	}

	public void setDuplicatedFilesCount(long duplicatedFilesCount)
	{
		this.duplicatedFilesCount = duplicatedFilesCount;
	}

	public List<FileState> getDuplicates()
	{
		return duplicates;
	}

	public void setDuplicates(List<FileState> duplicates)
	{
		this.duplicates = duplicates;
	}

	private class HashComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.getHash().compareTo(fs2.getHash());
		}
	}
}
