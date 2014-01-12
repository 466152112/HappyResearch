package happy.research.cf;

import happy.coding.io.FileIO;

import java.io.File;

public class ReadResults
{
	public static void ReadFolder(String dirPath) throws Exception
	{
		dirPath = FileIO.makeDirPath(dirPath);
		File dir = new File(dirPath);
		if (!dir.isDirectory()) throw new Exception(dirPath + " is not a directory");

		File[] files = dir.listFiles();
		for (File file : files)
		{
			String results = file.getName() + "\r\n";
			results += FileIO.readAsString(file.getPath(), new String[] { "MAE", "MAUE" });
			FileIO.writeString(dirPath + "Results.txt", results, true);
		}
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception
	{
		ReadFolder("Results\\FilmTrust");
	}

}
