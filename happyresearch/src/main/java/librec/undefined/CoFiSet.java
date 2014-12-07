package librec.undefined;

import java.util.List;

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.intf.IterativeRecommender;

/**
 * Pan and Chen, CoFiSet: Collaborative Filtering via Learning Pairwise
 * Preferences over Item-sets, SDM 2013.
 * 
 * @author guoguibing
 * 
 */
public class CoFiSet extends IterativeRecommender {

	public CoFiSet(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();

		itemBias = new DenseVector(numItems);
		itemBias.init(0.01);
	}

	@Override
	protected void buildModel() throws Exception {
		// outer loop
		for (int iter = 1; iter < numIters; iter++) {
			// inner loop
			for (int t = 0; t < numUsers; t++) {
				
			}
		}
	}

	@Override
	protected double predict(int u, int j) {
		return itemBias.get(j) + DenseMatrix.rowMult(P, u, Q, j);
	}

	protected double predict(int u, List<Integer> items) {
		double sum = 0;
		for (int j : items) {
			sum += predict(u, j);
		}
		return sum / items.size();
	}

}
