import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class DuplicateFinder
{
	public long duplicatesCount;
	public List<FileState> duplicates;

	private final boolean verbose;
	private Comparator<FileState> hashComparator;

	public DuplicateFinder(boolean verbose)
	{
		this.verbose = verbose;
		this.hashComparator = new HashComparator();
	}

	public void findDuplicates(State state)
	{
		List<FileState> fileStates = new ArrayList<>(state.fileStates);
		Collections.sort(fileStates, hashComparator);

		duplicatesCount = 0;
		duplicates = new ArrayList<>();
		String previousHash = "";
		for (FileState fileState : fileStates)
		{
			if (!previousHash.equals(fileState.hash))
			{
				takeInAccountDuplicates();
				duplicates.clear();
			}

			previousHash = fileState.hash;
			duplicates.add(fileState);
		}
		takeInAccountDuplicates();

		System.out.println("");
		System.out.println(duplicatesCount + " duplicated files");
	}

	private void takeInAccountDuplicates()
	{
		if (duplicates.size() > 1)
		{
			if (verbose)
			{
				for (FileState fs : duplicates)
				{
					System.out.println("  " + fs.fileName);
				}
				System.out.println("------------------------------------------------------------");
			}
			duplicatesCount += duplicates.size() - 1;
		}
	}

	private class HashComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.hash.compareTo(fs2.hash);
		}
	}
}
