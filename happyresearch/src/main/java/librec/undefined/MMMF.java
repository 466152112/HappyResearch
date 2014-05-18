// Copyright (C) 2014 Guibing Guo
//
// This file is part of LibRec.
//
// LibRec is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// LibRec is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

package librec.undefined;

import librec.data.SparseMatrix;
import librec.intf.IterativeRecommender;

/**
 * Weimer et al., <strong>Improving Maximum Margin Matrix
 * Factorization</strong>, Machine Learning, 2008. <br>
 * 
 * Original implementation in Matlab: http://ttic.uchicago.edu/~nati/mmmf/code.html
 * 
 * @author guoguibing
 * 
 */
public class MMMF extends IterativeRecommender {

	public MMMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "MMMF";
		isRankingPred = true;
	}

}
