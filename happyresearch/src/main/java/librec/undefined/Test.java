package librec.undefined;

import librec.data.DataDAO;

public class Test {

	public static void main(String[] args) throws Exception {
		String path = "D:\\Java\\Datasets\\Epinions\\trust.txt";
		DataDAO dao = new DataDAO(path);
		dao.printSpecs();
	}
}
