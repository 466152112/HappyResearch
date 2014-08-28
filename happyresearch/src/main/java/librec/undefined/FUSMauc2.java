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
 * FUSM: Factored User Similarity Models for Top-N Recommender Systems
 * 
 * @author guoguibing
 * 
 */
public class FUSMauc2 extends IterativeRecommender {

	private int rho;
	private double alpha, regBeta, regGamma;

	public FUSMauc2(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numUsers, numFactors);
		P.init(0.01);
		Q.init(0.01);

		itemBiases = new DenseVector(numItems);
		itemBiases.init(0.01);

		rho = cf.getInt("FISM.rho");
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

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numUsers, numFactors);

			// update throughout each user-item-rating (u, j, ruj) cell 
			for (int u : trainMatrix.rows()) {
				SparseVector Ru = trainMatrix.row(u);

				for (VectorEntry ve : Ru) {
					int i = ve.index();
					double rui = ve.get();

					SparseVector Ci = trainMatrix.column(i);

					// make a random sample of negative feedback (total - nnz)
					List<Integer> indices = null, unratedItems = new ArrayList<>();
					try {
						indices = Randoms.randInts(rho, 0, numItems - Ru.getCount());
					} catch (Exception e) {
						e.printStackTrace();
					}
					int index = 0, count = 0;
					for (int j = 0; j < numItems; j++) {
						if (!Ru.contains(j)) {
							if (count++ == indices.get(index)) {
								unratedItems.add(j);
								index++;
								if (index >= indices.size())
									break;
							}
						}
					}

					double wi = Ci.getCount() - 1 > 0 ? Math.pow(Ci.getCount() - 1, -alpha) : 0;
					double sum_i = 0;
					for (VectorEntry vk : Ci) {
						// for test, i and j will be always unequal as j is unrated
						int v = vk.index();
						if (u != v)
							sum_i += DenseMatrix.rowMult(P, v, Q, u);
					}

					double[] sum_if = new double[numFactors];
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ci) {
							int v = vk.index();
							if (v != u)
								sum_if[f] += P.get(v, f);
						}
					}

					// update for each unrated item
					for (int j : unratedItems) {

						SparseVector Cj = trainMatrix.column(j);
						double sum_j = 0;
						for (VectorEntry vk : Cj) {
							int v = vk.index();
							sum_j += DenseMatrix.rowMult(P, v, Q, u);
						}
						double wj = Cj.getCount() > 0 ? Math.pow(Cj.getCount(), -alpha) : 0;

						double bi = itemBiases.get(i), bj = itemBiases.get(j);
						double pui = bi + wi * sum_i;
						double puj = bj + wj * sum_j;
						double ruj = 0;
						double eij = (rui - ruj) - (pui - puj);

						errs += eij * eij;
						loss += eij * eij;

						// update bi
						itemBiases.add(i, lRate * (eij - regGamma * bi));

						// update bj
						itemBiases.add(j, -lRate * (eij - regGamma * bj));

						loss += regGamma * bi * bi + regGamma * bj * bj;

						// update quf
						for (int f = 0; f < numFactors; f++) {
							double quf = Q.get(u, f);

							double sum_jf = 0;
							for (VectorEntry vk : Cj) {
								int v = vk.index();
								sum_jf += P.get(v, f);
							}

							double delta = eij * (wj * sum_jf - wi * sum_if[f]) + regBeta * quf;
							QS.add(u, f, -lRate * delta);

							loss += regBeta * quf * quf;
						}

						// update pvf for v in Ci, and v in Cj
						for (VectorEntry vk : Ci) {
							int v = vk.index();
							if (v != u) {
								for (int f = 0; f < numFactors; f++) {
									double pvf = P.get(v, f), quf = Q.get(u, f);
									double delta = eij * wi * quf - regBeta * pvf;

									if (Cj.contains(v))
										delta -= eij * wj * quf;

									PS.add(v, f, lRate * delta);
									loss += regBeta * pvf * pvf;
								}
							}
						}

						for (VectorEntry vk : Cj) {
							int v = vk.index();
							if (Ci.contains(v))
								continue;

							for (int f = 0; f < numFactors; f++) {
								double pvf = P.get(v, f), quf = Q.get(u, f);
								double delta = eij * wj * quf - regBeta * pvf;

								PS.add(v, f, -lRate * delta);

								loss -= regBeta * pvf * pvf;
							}
						}
					}

				}

			}

			P = P.add(PS);
			Q = Q.add(QS);

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

		SparseVector Ci = trainMatrix.column(i);
		for (VectorEntry ve : Ci) {
			int v = ve.index();
			// for test, i and j will be always unequal as j is unrated
			if (v != u) {
				sum += DenseMatrix.rowMult(P, v, Q, u);
				count++;
			}
		}
		double wi = count > 0 ? Math.pow(count, -alpha) : 0;

		return itemBiases.get(i) + wi * sum;
	}

	@Override
	public String toString() {
		return super.toString() + ","
				+ Strings.toString(new Object[] { rho, (float) alpha, (float) regBeta, (float) regGamma }, ",");
	}
}