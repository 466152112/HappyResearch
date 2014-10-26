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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import happy.coding.io.Logs;
import happy.coding.io.Strings;
import happy.coding.math.Randoms;
import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.SocialRecommender;

/**
 * Social Bayesian Personalized Ranking
 * 
 * <p>
 * Zhao et al., <strong>Leveraing Social Connections to Improve Personalized
 * Ranking for Collaborative Filtering</strong>, CIKM 2014.
 * </p>
 * 
 * @author guibing
 * 
 */
public class SBPR extends SocialRecommender {

	private Multimap<Integer, Integer> M, SP, N;

	public SBPR(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
		initByNorm = false;
	}

	@Override
	protected void initModel() throws Exception {
		// initialization
		super.initModel();

		itemBiases = new DenseVector(numItems);
		itemBiases.init();

		// split items into positive, social positive, negative categories
		M = HashMultimap.create();
		SP = HashMultimap.create();
		N = HashMultimap.create();

		for (int u = 0, um = trainMatrix.numRows(); u < um; u++) {
			// Pu
			SparseVector Ru = trainMatrix.row(u);
			if (Ru.getCount() == 0)
				continue; // no rated items
			for (VectorEntry ve : Ru)
				M.put(u, ve.index());

			// SPu
			Set<Integer> SPu = new HashSet<>();
			SparseVector Tu = socialMatrix.row(u);
			for (VectorEntry ve : Tu) {
				int v = ve.index(); // friend v
				if (v >= um)
					continue;

				SparseVector Rv = trainMatrix.row(v); // v's ratings
				for (VectorEntry ve2 : Rv) {
					int j = ve2.index(); // v's rated items
					if (!Ru.contains(j)) // if not rated by user u
					{
						SP.put(u, j);
						SPu.add(j);
					}
				}
			}

			// Nu
			for (int k = 0; k < numItems; k++) {
				if (Ru.contains(k) || SPu.contains(k))
					continue;
				N.put(u, k);
			}
		}
	}

	@Override
	protected void buildModel() throws Exception {

		for (int iter = 1; iter <= numIters; iter++) {

			if (verbose)
				Logs.debug("Fold {} runs at iteration = {}", fold, iter);

			for (int s = 0, smax = numUsers * 100; s < smax; s++) {

				// uniformly draw (u, i, k, j)
				int u = Randoms.uniform(trainMatrix.numRows());

				// Pu
				List<Integer> Pu = new ArrayList<>(M.get(u));
				if (Pu.size() == 0)
					continue; // no rated item

				int i = Pu.get(Randoms.uniform(Pu.size()));
				double xui = predict(u, i);

				// Nu
				List<Integer> Nu = new ArrayList<>(N.get(u));
				int j = Nu.get(Randoms.uniform(Nu.size()));
				double xuj = predict(u, j);

				// SPu
				List<Integer> SPu = new ArrayList<>(SP.get(u));
				if (SPu.size() > 0) {
					// if having social neighbors
					int k = SPu.get(Randoms.uniform(SPu.size()));
					double xuk = predict(u, k);

					// double suk = 1; // simple way: constant

					// better way: count the number of neighbors who rated item
					// k that user u did not rate
					SparseVector Tu = socialMatrix.row(u);
					double suk = 0;
					for (VectorEntry ve : Tu) {
						int v = ve.index();
						double rvk = trainMatrix.get(v, k);
						if (rvk > 0)
							suk += 1;
					}

					double xuik = (xui - xuk) / (1 + suk);
					double xukj = xuk - xuj;

					double cik = 1.0 / (1 + Math.exp(xuik));
					double ckj = 1.0 / (1 + Math.exp(xukj));

					// update bi, bk, bj
					double bi = itemBiases.get(i);
					itemBiases.add(i, lRate * (cik / (1 + suk) + regI * bi));

					double bk = itemBiases.get(k);
					itemBiases.add(k, lRate
							* (-cik / (1 + suk) + ckj + regI * bk));

					double bj = itemBiases.get(j);
					itemBiases.add(j, lRate * (-ckj + regI * bj));

					// update P, Q
					for (int f = 0; f < numFactors; f++) {
						double puf = P.get(u, f);
						double qif = Q.get(i, f), qkf = Q.get(k, f);
						double qjf = Q.get(j, f);

						double delta_puf = cik * (qif - qkf) / (1 + suk) + ckj
								* (qkf - qjf);
						P.add(u, f, lRate * (delta_puf + regU * puf));

						Q.add(i, f, lRate
								* (cik * puf / (1 + suk) + regI * qif));

						double delta_qkf = cik * (-puf / (1 + suk)) + ckj * puf;
						Q.add(k, f, lRate * (delta_qkf + regI * qkf));

						Q.add(j, f, lRate * (cik * (-puf) + regI * qjf));
					}
				} else {
					// if no social neighbors, the same as BPRMF
					double xuij = xui - xuj;
					double cij = 1.0 / (1 + Math.exp(xuij));

					for (int f = 0; f < numFactors; f++) {
						double puf = P.get(u, f);
						double qif = Q.get(i, f);
						double qjf = Q.get(j, f);

						P.add(u, f, lRate * (cij * (qif - qjf) + regU * puf));
						Q.add(i, f, lRate * (cij * puf + regI * qif));
						Q.add(j, f, lRate * (cij * (-puf) + regI * qjf));
					}
				}
			}

		}
	}

	@Override
	protected double predict(int u, int j) {
		return itemBiases.get(j) + DenseMatrix.rowMult(P, u, Q, j);
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { numFactors, lRate, regU, regI,
				numIters }, ",");
	}

}
