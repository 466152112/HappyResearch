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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with LibRec. If not, see <http://www.gnu.org/licenses/>.
//

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
