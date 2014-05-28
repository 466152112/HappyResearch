package librec.undefined;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.system.Debug;

import java.io.BufferedReader;

import librec.data.DataConvertor;
import librec.data.DataDAO;
import librec.data.SparseMatrix;

import org.junit.Test;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class UnitTests {

	@Test
	public void testCvtFirstLines() throws Exception {
		String dirPath = "D:\\Research\\Datasets\\KDD Cup 2011\\Yahoo! Music Dataset\\track2\\";
		String sourcePath = dirPath + "trainIdx2.firstLines.txt";
		String targetPath = dirPath + "train.txt";

		// train data set
		DataConvertor dc = new DataConvertor(sourcePath, targetPath);
		dc.cvtFirstLines("\\|", "\t");
		
		// test data set
		dc.setSourcePath(dirPath+"testIdx2.firstLines.txt");
		dc.setTargetPath(dirPath+"test.txt");
		dc.cvtFirstLines("\\|", "\t");
	}

	@Test
	public void testSparseMatrix() throws Exception {
		Table<Integer, Integer, Double> vals = HashBasedTable.create();
		vals.put(0, 0, 10.0);
		vals.put(0, 4, -2.0);
		vals.put(1, 0, 3.0);
		vals.put(1, 1, 9.0);
		vals.put(1, 5, 3.0);
		vals.put(2, 1, 7.0);
		vals.put(2, 2, 8.0);
		vals.put(2, 3, 7.0);
		vals.put(3, 0, 3.0);
		vals.put(3, 2, 8.0);
		vals.put(3, 3, 7.0);
		vals.put(3, 4, 5.0);
		vals.put(4, 1, 8.0);
		vals.put(4, 3, 9.0);
		vals.put(4, 4, 9.0);
		vals.put(4, 5, 13.0);
		vals.put(5, 1, 4.0);
		vals.put(5, 4, 2.0);
		vals.put(5, 5, -1.0);

		SparseMatrix A = new SparseMatrix(6, 6, vals);
		Logs.debug(A);

		Logs.debug(Strings.toString(A.rowList()));
		Logs.debug(Strings.toString(A.columnList()));
	}

	public static void main(String[] args) throws Exception {
		String dirPath = "D:\\Java\\Datasets\\BookCrossing\\";

		String path = dirPath + "ratings.txt";
		DataDAO dao = new DataDAO(path);
		// dao.readData(new int[] { 0, 1 }, false);
		if (Debug.OFF) {
			dao.printSpecs();
		} else if (Debug.ON) {
			dao.printDistr(true);
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
