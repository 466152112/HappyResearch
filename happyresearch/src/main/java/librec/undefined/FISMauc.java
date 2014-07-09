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

import happy.coding.io.Strings;
import happy.coding.math.Randoms;

import java.util.ArrayList;
import java.util.List;

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.IterativeRecommender;

/**
 * Kabbur et al., FISM: Factored Item Similarity Models for Top-N Recommender
 * Systems, KDD 2013.
 * 
 * @author guoguibing
 * 
 */
public class FISMauc extends IterativeRecommender {

	private int rho;
	private double alpha, regBeta, regGamma;

	public FISMauc(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		P = new DenseMatrix(numItems, numFactors);
		Q = new DenseMatrix(numItems, numFactors);
		P.init(0.01);
		Q.init(0.01);

		itemBiases = new DenseVector(numItems);
		itemBiases.init(0.01);

		rho = (int) cf.getDouble("FISM.rho");
		alpha = cf.getDouble("FISM.alpha");

		regBeta = cf.getDouble("FISM.reg.beta");
		regGamma = cf.getDouble("FISM.reg.gamma");

		// pre-processing: binarize training data
		super.binary(trainMatrix);
	}

	@Override
	protected void buildModel() {

		for (int iter = 1; iter <= numIters; iter++) {

			errs = 0;
			loss = 0;

			// update throughout each user-item-rating (u, j, ruj) cell 
			for (int u : trainMatrix.rows()) {
				SparseVector Ru = trainMatrix.row(u);

				for (VectorEntry ve : Ru) {
					int i = ve.index();
					double rui = ve.get();

					// make a random sample of negative feedback (total - nnz)
					List<Integer> indices = null, items = new ArrayList<>();
					try {
						indices = Randoms.randInts(rho, 0, numItems - Ru.getCount());
					} catch (Exception e) {
						e.printStackTrace();
					}
					int index = 0, count = 0;
					for (int j = 0; j < numItems; j++) {
						if (!Ru.contains(j) && count++ == indices.get(index)) {
							items.add(j);
							index++;
							if (index >= indices.size())
								break;
						}
					}

					double wu = Math.pow(Ru.getCount() - 1, -alpha);
					double[] x = new double[numFactors];
					for (int j : items) {

						double pui = predict(u, i), puj = predict(u, j), ruj = 0;
						double eij = (rui - ruj) - (pui - puj);

						errs += eij * eij;
						loss += eij * eij;

						// update bi
						double bi = itemBiases.get(i);
						itemBiases.add(i, lRate * (eij - regGamma * bi));

						// update bj
						double bj = itemBiases.get(j);
						itemBiases.add(j, -lRate * (eij - regGamma * bj));

						loss += regGamma * bi * bi + regGamma * bj * bj;

						// update qif, qjf
						for (int f = 0; f < numFactors; f++) {
							double qif = Q.get(i, f), qjf = Q.get(j, f);

							double sum_k = 0;
							for (VectorEntry vk : Ru) {
								int k = vk.index();
								if (k != i) {
									sum_k += P.get(k, f);
								}
							}

							double delta_i = eij * wu * sum_k - regBeta * qif;
							Q.add(i, f, lRate * delta_i);

							double delta_j = eij * wu * sum_k - regBeta * qjf;
							Q.add(j, f, -lRate * delta_j);

							x[f] += eij * (qif - qjf);

							loss += regBeta * qif * qif + regBeta * qjf * qjf;
						}
					}

					for (VectorEntry vk : Ru) {
						int j = vk.index();
						if (j != i) {
							for (int f = 0; f < numFactors; f++) {
								double pjf = P.get(j, f);
								double delta = wu * x[f] / rho - regBeta * pjf;

								P.add(j, f, lRate * delta);

								loss += regBeta * pjf * pjf;
							}
						}
					}
				}

			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int i) {

		double sum = 0;
		int count = 0;

		SparseVector Ru = trainMatrix.row(u);
		for (VectorEntry ve : Ru) {
			int j = ve.index();
			// for test, i and j will be always unequal as j is unrated
			if (i != j) {
				sum += DenseMatrix.rowMult(P, j, Q, i);
				count++;
			}
		}

		return itemBiases.get(i) + Math.pow(count, -alpha) * sum;
	}

	@Override
	public String toString() {
		return super.toString() + ","
				+ Strings.toString(new Object[] { rho, (float) alpha, (float) regBeta, (float) regGamma }, ",");
	}
}
