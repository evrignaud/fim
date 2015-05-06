import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by evrignaud on 05/05/15.
 */
public class DuplicateFinder
{
	private Comparator<FileState> hashComparator = new HashComparator();

	public void findDuplicates(State state, boolean verbose)
	{
		List<FileState> fileStates = new ArrayList<>(state.fileStates);
		Collections.sort(fileStates, hashComparator);

		long duplicatesCount = 0;
		List<FileState> duplicates = new ArrayList<>();
		String previousHash = "";
		for (FileState fileState : fileStates)
		{
			if (!previousHash.equals(fileState.hash))
			{
				if (duplicates.size() > 1)
				{
					if (verbose)
					{
						for (FileState fs : duplicates)
						{
							System.out.println(fs.fileName);
						}
						System.out.println("------------------------------");
					}
					duplicatesCount++;
				}

				duplicates.clear();
			}

			previousHash = fileState.hash;
			duplicates.add(fileState);
		}

		System.out.println("");
		System.out.println(duplicatesCount + " duplicated files");
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
