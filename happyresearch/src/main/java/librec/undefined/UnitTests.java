package librec.undefined;

import happy.coding.io.FileIO;
import happy.coding.io.Logs;
import happy.coding.math.Randoms;
import happy.coding.system.Debug;
import happy.coding.system.Systems;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import librec.data.DataConvertor;
import librec.data.DataDAO;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.SymmMatrix;

import org.junit.Test;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class UnitTests {

	@Test
	public void testSerialization() throws Exception {
		String filePath = Systems.getDesktop() + "vec.dat";

		DenseVector vec = new DenseVector(11);
		for (int i = 10, j = 0; i >= 0; i--, j++)
			vec.set(j, i);

		FileIO.serialize(vec, filePath);

		DenseVector v2 = (DenseVector) FileIO.deserialize(filePath);
		Logs.debug(v2.toString());

		DenseMatrix mat = new DenseMatrix(3, 4);
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 4; j++)
				mat.set(i, j, i + j);
		Logs.debug(mat);

		String matPath = Systems.getDesktop() + "mat.dat";
		FileIO.serialize(mat, matPath);

		DenseMatrix mat2 = (DenseMatrix) FileIO.deserialize(matPath);
		Logs.debug(mat2);

	}

	@Test
	public void testCvtFirstLines() throws Exception {
		String dirPath = "D:\\Research\\Datasets\\KDD Cup 2011\\Yahoo! Music Dataset\\track2\\";
		String sourcePath = dirPath + "trainIdx2.firstLines.txt";
		String targetPath = dirPath + "train.txt";

		// train data set
		DataConvertor dc = new DataConvertor(sourcePath, targetPath);
		dc.cvtFirstLines("\\|", "\t");

		// test data set
		dc.setSourcePath(dirPath + "testIdx2.firstLines.txt");
		dc.setTargetPath(dirPath + "test.txt");
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

		String dirPath = FileIO.desktop;
		FileIO.serialize(A, dirPath + "A.mat");

		SparseMatrix A2 = (SparseMatrix) FileIO.deserialize(dirPath + "A.mat");
		Logs.debug(A2);

		SparseVector v = new SparseVector(10);
		v.set(2, 5);
		v.set(9, 10);
		Logs.debug(v);

		FileIO.serialize(v, dirPath + "v.vec");
		SparseVector v2 = (SparseVector) FileIO.deserialize(dirPath + "v.vec");
		Logs.debug(v2);

		SymmMatrix mm = new SymmMatrix(5);
		mm.set(0, 1, 0.5);
		mm.set(2, 3, 0.3);
		mm.set(4, 2, 0.8);
		Logs.debug(mm);

		FileIO.serialize(mm, dirPath + "mm.mat");
		SymmMatrix mm2 = (SymmMatrix) FileIO.deserialize(dirPath + "mm.mat");
		Logs.debug(mm2);

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

	@Test
	public void testSample() throws Exception {
		String dir = "D:\\Dropbox\\PhD\\My Work\\Experiments\\Datasets\\Ratings\\Epinions\\Extended Epinions dataset\\";
		String dirDest = dir + "Distrust v1\\";

		// read trust data to get all users
		Set<Long> users = new HashSet<>();
		BufferedReader br = FileIO.getReader(dirDest + "trust.txt");
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\t");
			long trustor = Long.parseLong(data[0]);
			long trustee = Long.parseLong(data[1]);

			users.add(trustor);
			users.add(trustee);
		}
		br.close();

		// retrieve ratings given by the above users;
		br = FileIO.getReader(dir + "rating.txt");
		line = null;
		List<String> lines = new ArrayList<>(1500);
		String file = dirDest + "ratings.txt";
		FileIO.deleteFile(file);

		while ((line = br.readLine()) != null) {
			String[] data = line.split("[ \t,]");

			String item = data[0];
			long user = Long.parseLong(data[1]);
			String rate = data[2];

			if (users.contains(user)) {
				lines.add(user + "\t" + item + "\t" + rate);

				if (lines.size() >= 1000) {
					FileIO.writeList(file, lines, true);
					lines.clear();
				}
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(file, lines, true);

		Logs.debug("Done!");
	}

	@Test
	public void testSample2() throws Exception {
		String dir = "D:\\Dropbox\\PhD\\My Work\\Experiments\\Datasets\\Ratings\\Epinions\\Extended Epinions dataset\\";
		String dirDest = dir + "Distrust_v5\\";

		FileIO.makeDirectory(dirDest);

		// read trust data to get all users
		Set<Long> allUsers = new HashSet<>();
		BufferedReader br = FileIO.getReader(dir + "user_rating.txt");
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] data = line.split("\t");
			long trustor = Long.parseLong(data[0]);
			long trustee = Long.parseLong(data[1]);

			allUsers.add(trustor);
			allUsers.add(trustee);
		}
		br.close();

		// sample 20_000 users from all users;
		List<Long> users = new ArrayList<>(allUsers);
		List<Long> sample = new ArrayList<>();
		for (int idx : Randoms.randInts(4_000, 0, users.size()))
			sample.add(users.get(idx));

		// retrieve trusts containing trustors, trustees in sample
		br = FileIO.getReader(dir + "user_rating.txt");
		line = null;
		List<String> lines = new ArrayList<>(1500);
		String file = dirDest + "trust.txt";
		FileIO.deleteFile(file);

		while ((line = br.readLine()) != null) {
			String[] data = line.split("[ \t,]");

			long trustor = Long.parseLong(data[0]);
			long trustee = Long.parseLong(data[1]);
			String rate = data[2];

			if (sample.contains(trustor) && sample.contains(trustee)) {
				lines.add(trustor + "\t" + trustee + "\t" + rate);

				if (lines.size() >= 1000) {
					FileIO.writeList(file, lines, true);
					lines.clear();
				}
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(file, lines, true);

		// retrieve ratings given by the above users;
		br = FileIO.getReader(dir + "rating.txt");
		line = null;
		lines.clear();
		file = dirDest + "ratings.txt";
		FileIO.deleteFile(file);

		while ((line = br.readLine()) != null) {
			String[] data = line.split("[ \t,]");

			String item = data[0];
			long user = Long.parseLong(data[1]);
			double rate = Double.parseDouble(data[2]);
			rate = rate > 5 ? 5.0 : rate;

			if (sample.contains(user)) {
				lines.add(user + "\t" + item + "\t" + rate);

				if (lines.size() >= 1000) {
					FileIO.writeList(file, lines, true);
					lines.clear();
				}
			}
		}

		if (lines.size() > 0)
			FileIO.writeList(file, lines, true);

		Logs.debug("Done!");
	}
}
