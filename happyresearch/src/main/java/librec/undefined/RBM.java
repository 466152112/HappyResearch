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
 * Salakhutdinov et al., <strong>Restricted Boltzmann Machines for Collaborative
 * Filtering</strong>, ICML 2007.
 * 
 * 
 * <p>
 * Related Work:
 * <ul>
 * <li>Gunawardana and Meek, Tied Boltzmann Machines for Cold Start
 * Recommendations, RecSys 2008.</li>
 * <li>Section 2.4 of Jahrer and Toscher, Collaborative Filtering Ensemble,
 * JMLR, 2012.</li>
 * <li>Edwin Chen's Blog: <a href=
 * "http://blog.echen.me/2011/07/18/introduction-to-restricted-boltzmann-machines/"
 * >Introduction to Restricted Boltzmann Machines</a>, <a
 * href="https://github.com/echen/restricted-boltzmann-machines">source code</a>
 * </li>
 * <li>Kai Lu's talk: <a
 * href="http://classes.soe.ucsc.edu/cmps290c/Spring13/proj/kailu_talk.pdf">The
 * Application of Deep Learning in Collaborative Filtering</a></li>
 * </ul>
 * </p>
 * 
 * @author guoguibing
 * 
 */
public class RBM extends IterativeRecommender {

	public RBM(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);
	}

}
