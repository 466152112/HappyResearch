package librec.undefined;

import librec.data.SparseMatrix;
import librec.intf.SocialRecommender;

/**
 * Hao Ma, Haixuan Yang, Michael R. Lyu and Irwin King, <strong>SoRec: Social
 * recommendation using probabilistic matrix factorization</strong>, ACM CIKM
 * 2008.
 * 
 * @author guoguibing
 * 
 */
public class SoRec extends SocialRecommender {

	public SoRec(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "SoRec";
	}

}
