package librec.undefined;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

import librec.data.DataDAO;

import org.junit.Test;

import happy.coding.io.FileIO;

public class CiaoTest {

	@Test
	public void ratings() throws Exception {
		String dirPath = "D:\\Research\\Datasets\\Ciao\\";
		String dest = dirPath + "formatted\\" + "ratings.txt";

		FileIO.deleteFile(dest);

		BufferedReader br = FileIO.getReader(dirPath
				+ "rating_with_timestamp.txt");
		String line = null;
		List<String> lines = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			String[] data = line.trim().split("  ");

			int user = (int) Double.parseDouble(data[0]);
			int item = (int) Double.parseDouble(data[1]);
			double rate = Double.parseDouble(data[3]);

			line = user + " " + item + " " + rate;
			lines.add(line);

			if (lines.size() >= 1000) {
				FileIO.writeList(dest, lines, true);
				lines.clear();
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(dest, lines, true);

		br.close();
	}

	@Test
	public void trust() throws Exception {
		String dirPath = "D:\\Research\\Datasets\\Ciao\\";
		String dest = dirPath + "formatted\\" + "trust.txt";

		FileIO.deleteFile(dest);

		BufferedReader br = FileIO.getReader(dirPath + "trust.txt");
		String line = null;
		List<String> lines = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			String[] data = line.trim().split("  ");

			int trustor = (int) Double.parseDouble(data[0]);
			int trustee = (int) Double.parseDouble(data[1]);

			line = trustor + " " + trustee + " " + 1.0;
			lines.add(line);

			if (lines.size() >= 1000) {
				FileIO.writeList(dest, lines, true);
				lines.clear();
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(dest, lines, true);

		br.close();
	}

	@Test
	public void specs() throws Exception {
		String dir = "D:\\Java\\Datasets\\Ciao\\V2\\";
		DataDAO dao = new DataDAO(dir + "trust.txt");
		dao.printSpecs();
	}

}
