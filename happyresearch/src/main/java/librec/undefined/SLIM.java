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
import happy.coding.io.Lists;
import happy.coding.io.Logs;
import happy.coding.io.Strings;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import librec.data.DenseMatrix;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.SymmMatrix;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Xia Ning and George Karypis, <strong>SLIM: Sparse Linear Methods for Top-N
 * Recommender Systems</strong>, ICDM 2011. <br>
 * 
 * <p>
 * Related Work:
 * <ul>
 * <li>Levy and Jack, Efficient Top-N Recommendation by Linear Regression, ISRS
 * 2013. This paper reports experimental results on the MovieLens (100K, 10M)
 * and Epinions datasets in terms of precision, MRR and HR@N. According to the
 * results, the performance of SLIM works only slightly better than ItemKNN.</li>
 * <li>C++ Code: <a
 * href="http://www-users.cs.umn.edu/~xning/slim/html/index.html">Slim</a></li>
 * <li>Python Code: <a href=
 * "https://github.com/Mendeley/mrec/blob/master/mrec/item_similarity/slim.py"
 * >mrec: slim.py</a></li>
 * <li>C# Code: <a href="">MyMediaLite: SLIM.cs</a></li>
 * <li>Friedman et al., Regularization Paths for Generalized Linear Models via
 * Coordinate Descent, Journal of Statistical Software, 2010.</li>
 * </ul>
 * </p>
 * 
 * @author guoguibing
 * 
 */
public class SLIM extends IterativeRecommender {

	private DenseMatrix itemWeights; // ~ W
	private SymmMatrix itemCorrs;
	private int knn;

	private Table<Integer, Integer, Double> nnsTable;

	// regularization parameters for the L1 or L2 term 
	private double regL1, regL2;

	public SLIM(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
		knn = cf.getInt("num.neighbors");

		regL1 = cf.getDouble("SLIM.reg.l1");
		regL2 = cf.getDouble("SLIM.reg.l2");
	}

	@Override
	protected void initModel() {
		itemWeights = new DenseMatrix(numItems, numItems);
		itemWeights.init();

		// find the nearest neighbors for each item based on item similarity
		itemCorrs = buildCorrs(false);
		nnsTable = HashBasedTable.create();

		for (int j = 0; j < numItems; j++) {
			// set diagonal entries to 0
			itemWeights.set(j, j, 0);

			// find the k-nearest neighbors for each item
			Map<Integer, Double> nns = itemCorrs.row(j).toMap();

			// sort by values to retriev topN similar items
			if (knn > 0 && knn < nns.size()) {
				List<KeyValPair<Integer>> sorted = Lists.sortMap(nns, true);
				List<KeyValPair<Integer>> subset = sorted.subList(0, knn);
				nns.clear();
				for (KeyValPair<Integer> kv : subset)
					nns.put(kv.getKey(), kv.getValue());
			}

			// put into the nns table
			for (Entry<Integer, Double> en : nns.entrySet())
				nnsTable.put(j, en.getKey(), en.getValue());
		}
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {

			if (verbose)
				Logs.debug("{} [{}] runs at iteration {}", algoName, fold, iter);

			// computing W_j for each item j
			for (int j = 0; j < numItems; j++) {

				// find k-nearest neighbors
				Map<Integer, Double> nns = nnsTable.row(j);

				// for each nearest neighbor i, update wij by the coordinate descent update rule
				for (Integer i : nns.keySet()) {
					if (j == i)
						continue;

					SparseVector Ri = trainMatrix.column(i);
					double gradSum = 0;
					for (VectorEntry ve : Ri) {
						int u = ve.index();
						double rui = ve.get();
						double ruj = trainMatrix.get(u, j);

						gradSum += rui * (ruj - predict(u, j, i));
					}

					double gradient = gradSum / Ri.getCount();

					if (regL1 < Math.abs(gradient)) {
						if (gradient > 0) {
							double update = (gradient - regL1) / (regL2 + 1.0);
							itemWeights.set(i, j, update);
						} else {
							double update = (gradient + regL1) / (regL2 + 1.0);
							itemWeights.set(i, j, update);
						}
					} else {
						itemWeights.set(i, j, 0.0);
					}
				}
			}
		}
	}

	protected double predict(int u, int j, int excluded_item) {
		// double pred = 0;

		Map<Integer, Double> nns = nnsTable.row(j);
		SparseVector Ru = trainMatrix.row(u);

		double sum = 0, weights = 0;
		for (VectorEntry ve : Ru) {
			int i = ve.index();
			double rui = ve.get();
			if (nns.containsKey(i) && i != excluded_item) {
				sum += rui * itemWeights.get(i, j);
				weights += rui;
			}
		}

		return sum / weights;
	}

	@Override
	protected double predict(int u, int j) {
		return predict(u, j, -1);
	}

	@Override
	public String toString() {
		return Strings.toString(
				new Object[] { knn, (float) regL1, (float) regL2, cf.getString("similarity"), maxIters }, ",");
	}

}
