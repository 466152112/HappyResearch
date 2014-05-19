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

import happy.coding.io.KeyValPair;
import happy.coding.io.Logs;
import happy.coding.math.Randoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.IterativeRecommender;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * 
 * Rendle et al., <strong>BPR: Bayesian Personalized Ranking from Implicit
 * Feedback</strong>, UAI 2009.
 * 
 * @author guoguibing
 * 
 */
public class BPRMF extends IterativeRecommender {

	private Multimap<Integer, KeyValPair<Integer>> D;
	private double regJ;

	public BPRMF(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "BPRMF";
		isRankingPred = true;

		regJ = cf.getDouble("BPRMF.reg.j");
	}

	@Override
	protected void initModel() {
		super.initModel();

		D = HashMultimap.create();

		for (int u = 0; u < numUsers; u++) {
			SparseVector pu = trainMatrix.row(u);
			for (int i : pu.getIndex()) {
				for (int j = 0; j < numItems; j++) {
					if (i != j) {
						// add one entry to Ds
						KeyValPair<Integer> pair = new KeyValPair<Integer>(i, j);
						D.put(u, pair);
					}
				}
			}
		}

	}

	@Override
	protected void buildModel() {

		List<Entry<Integer, KeyValPair<Integer>>> Ds = new ArrayList<>(D.entries());
		int size = Ds.size();
		Logs.debug("Total size = {}", size);

		for (int iter = 1; iter <= maxIters; iter++) {

			int sampleSize = numUsers * 100;
			for (int s = 0; s < sampleSize; s++) {

				// draw (u, i, j) from Ds with replacement
				Entry<Integer, KeyValPair<Integer>> entry = Ds.get(Randoms.uniform(size));
				int u = entry.getKey();
				KeyValPair<Integer> pair = entry.getValue();

				int i = pair.getKey();
				int j = pair.getVal().intValue();

				// update \theta 
				double xui = predict(u, i);
				double xuj = predict(u, j);
				double xuij = xui - xuj;

				double cmg = Math.exp(-xuij) / (1 + Math.exp(-xuij)) * lRate;

				for (int f = 0; f < numFactors; f++) {
					double wuf = P.get(u, f);
					double hif = Q.get(i, f);
					double hjf = Q.get(j, f);

					P.add(u, f, cmg * (hif - hjf) + regU * wuf);
					Q.add(i, f, cmg * wuf + regI * hif);
					Q.add(j, f, cmg * (-wuf) + regJ * hjf);
				}
			}
		}
	}
}
