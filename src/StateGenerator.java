import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by evrignaud on 05/05/15.
 */
public class StateGenerator
{
	private Comparator<FileState> fileNameComparator = new FileNameComparator();
	private ExecutorService executorService;
	private AtomicInteger count;

	public State generateState(String message, File baseDirectory) throws IOException
	{
		State state = new State();
		state.message = message;

		long start = System.currentTimeMillis();
		progressBarInit();

		int availableProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
		System.out.printf("Using the %d processors to compute file hashes%n", availableProcessors);
		executorService = Executors.newFixedThreadPool(availableProcessors);
		List<FileState> fileStates = new CopyOnWriteArrayList<>();
		getFileStates(fileStates, baseDirectory.toString(), baseDirectory);

		try
		{
			executorService.shutdown();
			executorService.awaitTermination(42, TimeUnit.DAYS);
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}

		state.fileStates = new ArrayList<>(fileStates);
		Collections.sort(state.fileStates, fileNameComparator);

		progressBarDone();
		displayTimeElapsed(start);

		return state;
	}

	private void displayTimeElapsed(long start)
	{
		long duration = System.currentTimeMillis() - start;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes);
		if (minutes == 0)
		{
			System.out.printf("File scan took %d sec%n%n", seconds);
		}
		else
		{
			System.out.printf("File scan took %d min, %d sec%n%n", minutes, seconds);
		}
	}

	private void getFileStates(List<FileState> fileStates, String baseDirectory, File directory)
	{
		File[] files = directory.listFiles();
		for (File file : files)
		{
			if (file.isDirectory() && file.getName().equals(".fic"))
			{
				continue;
			}

			if (file.isDirectory())
			{
				getFileStates(fileStates, baseDirectory, file);
			}
			else
			{
				executorService.submit(new FileHasher(fileStates, baseDirectory, file));
			}
		}
	}

	private class FileHasher implements Runnable
	{
		private List<FileState> fileStates;
		private final String baseDirectory;
		private File file;

		public FileHasher(List<FileState> fileStates, String baseDirectory, File file)
		{
			this.fileStates = fileStates;
			this.baseDirectory = baseDirectory;
			this.file = file;
		}

		@Override
		public void run()
		{
			updateProgressBar(file);

			String hash = hashFile(file);
			String fileName = file.toString();
			fileName = getRelativeFileName(baseDirectory, fileName);
			fileStates.add(new FileState(fileName, file.lastModified(), hash));
		}
	}

	private void progressBarInit()
	{
		count = new AtomicInteger(0);
	}

	private void updateProgressBar(File file)
	{
		int i = count.addAndGet(1);
		if (i % 10 == 0)
		{
			long fileMb = file.length() / 1024 / 1024;
			if (fileMb > 100)
			{
				System.out.print("O");
			}
			else if (fileMb > 20)
			{
				System.out.print("o");
			}
			else
			{
				System.out.print(".");
			}
		}

		if (i % 1000 == 0)
		{
			System.out.println("");
		}
	}

	private void progressBarDone()
	{
		if (count.get() > 10)
		{
			System.out.println("");
		}
	}

	private String getRelativeFileName(String baseDirectory, String fileName)
	{
		if (fileName.startsWith(baseDirectory))
		{
			fileName = fileName.substring(baseDirectory.length());
		}
		if (fileName.startsWith("/"))
		{
			fileName = fileName.substring(1);
		}
		return fileName;
	}

	private String hashFile(File file)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			FileInputStream fis = new FileInputStream(file);

			byte[] dataBytes = new byte[1024];

			int nread;
			while ((nread = fis.read(dataBytes)) != -1)
			{
				md.update(dataBytes, 0, nread);
			}

			byte[] mdbytes = md.digest();

			StringBuffer hexString = new StringBuffer();
			for (byte b : mdbytes)
			{
				hexString.append(String.format("%x", b));
			}

			return hexString.toString();

		}
		catch (Exception e)
		{
			e.printStackTrace();
			return "????";
		}
	}

	private class FileNameComparator implements Comparator<FileState>
	{
		@Override
		public int compare(FileState fs1, FileState fs2)
		{
			return fs1.fileName.compareTo(fs2.fileName);
		}
	}
}
