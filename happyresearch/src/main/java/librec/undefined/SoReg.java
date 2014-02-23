package librec.undefined;

import librec.data.SparseMatrix;
import librec.intf.SocialRecommender;

/**
 * Hao Ma, Dengyong Zhou, Chao Liu, Michael R. Lyu and Irwin King,
 * <strong>Recommender systems with social regularization</strong>, WSDM 2011.
 * 
 * @author guoguibing
 * 
 */
public class SoReg extends SocialRecommender {

	public SoReg(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "SoReg";
	}

}
