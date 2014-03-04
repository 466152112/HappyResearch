package librec.undefined;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;

import java.io.BufferedReader;
import java.util.HashSet;
import java.util.Set;

import librec.data.DataDAO;

import com.google.common.collect.BiMap;

public class Test {

	public static void main(String[] args) throws Exception {
		String dirPath = "D:\\Java\\Datasets\\FilmTrust\\";

		String path = dirPath + "ratings.txt";
		DataDAO dao = new DataDAO(path);
		dao.readData();
		//dao.printSpecs();
		BiMap<String, Integer> userIds = dao.getUserIds();

		String dataPath = dirPath + "trust.txt";
		BufferedReader br = FileIO.getReader(dataPath);
		String line = null;

		Set<Integer> commons = new HashSet<>();
		while ((line = br.readLine()) != null) {
			String[] data = line.split("[ \t,]");
			String user = data[0];

			if (userIds.containsKey(user))
				commons.add(userIds.get(user));
		}
		br.close();

		Logs.debug("Common size: {}", commons.size());

	}
}
