package librec.undefined;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.system.Debug;

import java.io.BufferedReader;

import librec.data.DataDAO;

import com.google.common.collect.BiMap;

public class Test {

	public static void main(String[] args) throws Exception {
		String dirPath = "D:\\Java\\Datasets\\MovieLens\\100K\\";

		String path = dirPath + "ratings.txt";
		DataDAO dao = new DataDAO(path);
		// dao.readData(new int[] { 0, 1 }, false);
		if (Debug.OFF) {
			dao.printSpecs();
		} else if (Debug.ON) {
			dao.printDistr();
		} else {
			BiMap<String, Integer> userIds = dao.getUserIds();

			String dataPath = dirPath + "review-ratings.txt";
			BufferedReader br = FileIO.getReader(dataPath);
			String line = null;

			while ((line = br.readLine()) != null) {
				String[] data = line.split("[ \t,]");
				String trustor = data[0];
				String trustee = data[1];

				if (!userIds.containsKey(trustor))
					userIds.put(trustor, userIds.size());

				if (!userIds.containsKey(trustee))
					userIds.put(trustee, userIds.size());
			}
			br.close();

			if (Debug.OFF) {
				br = FileIO.getReader(dirPath + "user-reviews.txt");
				line = null;

				while ((line = br.readLine()) != null) {
					String[] data = line.split("[ \t,]");
					String user = data[0];

					if (!userIds.containsKey(user))
						userIds.put(user, userIds.size());
				}
				br.close();
			}

			Logs.debug("Total users: {}", userIds.size());
		}
	}
}
