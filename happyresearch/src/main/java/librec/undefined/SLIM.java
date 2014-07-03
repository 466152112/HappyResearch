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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import librec.data.DenseMatrix;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.SymmMatrix;
import librec.intf.IterativeRecommender;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Xia Ning and George Karypis, <strong>SLIM: Sparse Linear Methods for Top-N
 * Recommender Systems</strong>, ICDM 2011. <br>
 * 
 * <p>
 * Related Work:
 * <ul>
 * <li>Levy and Jack, Efficient Top-N Recommendation by Linear Regression, ISRS
 * 2013. This paper reports experimental results on the MovieLens (100K, 10M)
 * and Epinions datasets in terms of precision, MRR and HR@N (i.e., Recall@N).
 * According to the results, the performance of SLIM works only slightly better
 * than ItemKNN.</li>
 * <li>C++ Code: <a
 * href="http://www-users.cs.umn.edu/~xning/slim/html/index.html">Slim</a></li>
 * <li>Python Code: <a href=
 * "https://github.com/Mendeley/mrec/blob/master/mrec/item_similarity/slim.py"
 * >mrec: slim.py</a></li>
 * <li>C# Code: MyMediaLite: SLIM.cs</li>
 * <li>Friedman et al., Regularization Paths for Generalized Linear Models via
 * Coordinate Descent, Journal of Statistical Software, 2010.</li>
 * </ul>
 * </p>
 * 
 * @author guoguibing
 * 
 */
public class SLIM extends IterativeRecommender {

	private DenseMatrix W; // ~ W
	private int knn;

	// item's nearest neighbors for kNN > 0
	private Multimap<Integer, Integer> itemNNs;

	// item's nearest neighbors for kNN <=0, i.e., all other items
	private List<Integer> allItems;

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
		W = new DenseMatrix(numItems, numItems);
		W.init();

		// standardize training matrix
		trainMatrix.standardize(false);

		if (knn > 0) {
			// find the nearest neighbors for each item based on item similarity
			SymmMatrix itemCorrs = buildCorrs(false);
			itemNNs = HashMultimap.create();

			for (int j = 0; j < numItems; j++) {
				// set diagonal entries to 0
				W.set(j, j, 0);

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

				// put into the nns multimap
				for (Entry<Integer, Double> en : nns.entrySet())
					itemNNs.put(j, en.getKey());
			}
		} else {
			// all items are used
			allItems = trainMatrix.columns();

			for (int j = 0; j < numItems; j++)
				W.set(j, j, 0.0);
		}
	}

	@Override
	protected void buildModel() {

		for (int j = 0; j < numItems; j++) {

			// computing Wj for each item j
			for (int iter = 1; iter <= maxIters; iter++) {

				if (verbose)
					Logs.debug("{} [{}] runs item {}/{} at iteration {}/{}", algoName, fold, j, numItems, iter,
							maxIters);

				// find k-nearest neighbors
				Collection<Integer> nns = knn > 0 ? itemNNs.get(j) : allItems;

				// all entries rated on item j 
				// SparseVector Rj = trainMatrix.column(j);

				// for each nearest neighbor i, update wij by the coordinate descent update rule
				for (Integer i : nns) {
					if (j == i)
						continue;

					double gradSum = 0, rateSum = 0;
					//for (VectorEntry ve : Rj) {
					for (int u = 0; u < numUsers; u++) {
						//int u = ve.index(); // u should be all users who have rated item j
						//double ruj = ve.get();
						double rui = trainMatrix.get(u, i);
						double ruj = trainMatrix.get(u, j);

						if (rui != 0) {
							gradSum += rui * (ruj - predict(u, j, i));
							rateSum += rui * rui; // useful if data is not standardized
						}
					}

					gradSum /= numUsers;
					rateSum /= numUsers;

					if (regL1 < Math.abs(gradSum)) {
						if (gradSum > 0) {
							double update = (gradSum - regL1) / (regL2 + rateSum);
							W.set(i, j, update);
						} else {
							double update = (gradSum + regL1) / (regL2 + rateSum);
							W.set(i, j, update);
						}
					} else {
						W.set(i, j, 0.0);
					}
				}
			}
		}
	}

	/**
	 * @return a prediction without the contribution of excludede_item
	 */
	protected double predict(int u, int j, int excluded_item) {

		Collection<Integer> nns = knn > 0 ? itemNNs.get(j) : allItems;
		SparseVector Ru = trainMatrix.row(u);

		double pred = 0;
		for (int k : nns) {
			if (Ru.contains(k) && k != excluded_item) {
				double ruk = Ru.get(k);
				pred += ruk * W.get(k, j);
			}
		}

		return pred;
	}

	@Override
	protected double predict(int u, int j) {
		return predict(u, j, -1);
	}

	protected double ranking_backup(int u, int j) {
		Collection<Integer> nns = knn > 0 ? itemNNs.get(j) : allItems;
		SparseVector Ru = trainMatrix.row(u);

		double pred = 0;
		for (int i : nns) {
			if (Ru.contains(i)) {
				pred += W.get(i, j);
			}
		}

		return pred;
	}

	@Override
	public String toString() {
		return Strings.toString(
				new Object[] { knn, (float) regL2, (float) regL1, cf.getString("similarity"), maxIters }, ",");
	}

}
