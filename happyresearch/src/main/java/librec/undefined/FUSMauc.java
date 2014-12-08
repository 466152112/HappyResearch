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
public class FUSMauc extends IterativeRecommender {

	private int rho;
	private float alpha;

	public FUSMauc(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		isRankingPred = true;
	}

	@Override
	protected void initModel() {
		P = new DenseMatrix(numUsers, numFactors);
		Q = new DenseMatrix(numUsers, numFactors);
		P.init(smallValue);
		Q.init(smallValue);

		itemBias = new DenseVector(numItems);
		itemBias.init(smallValue);

		rho = cf.getInt("FISM.rho");
		alpha = cf.getFloat("FISM.alpha");
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
					List<Integer> js = new ArrayList<>();
					int len = 0;
					while (len < rho) {
						int j = Randoms.uniform(numItems);
						if (Ru.contains(j) || js.contains(j))
							continue;

						js.add(j);
						len++;
					}

					double wi = Ci.getCount() - 1 > 0 ? Math.pow(Ci.getCount() - 1, -alpha) : 0;
					double sum_i = 0;
					double[] sum_if = new double[numFactors];
					for (int f = 0; f < numFactors; f++) {
						for (VectorEntry vk : Ci) {
							// for test, i and j will be always unequal as j is unrated
							int v = vk.index();
							if (u != v) {
								// sum_i += DenseMatrix.rowMult(P, v, Q, u);

								double pvf = P.get(v, f);
								sum_if[f] += pvf;
								sum_i += pvf * Q.get(u, f);
							}
						}
					}

					// update for each unrated item
					for (int j : js) {

						// declare variables first to speed up
						int v, f;
						double pvf, quf, delta;

						SparseVector Cj = trainMatrix.column(j);
						double sum_j = 0;
						double[] sum_jf = new double[numFactors];
						for (f = 0; f < numFactors; f++) {
							for (VectorEntry vk : Cj) {
								v = vk.index();
								// sum_j += DenseMatrix.rowMult(P, v, Q, u);

								pvf = P.get(v, f);
								sum_jf[f] += pvf;
								sum_j += pvf * Q.get(u, f);
							}
						}
						double wj = Cj.getCount() > 0 ? Math.pow(Cj.getCount(), -alpha) : 0;

						double bi = itemBias.get(i), bj = itemBias.get(j);
						double pui = bi + wi * sum_i;
						double puj = bj + wj * sum_j;
						double ruj = 0;
						double eij = (rui - ruj) - (pui - puj);

						errs += eij * eij;
						loss += eij * eij;

						// update bi
						itemBias.add(i, lRate * (eij - regB * bi));

						// update bj
						itemBias.add(j, -lRate * (eij - regB * bj));

						loss += regB * bi * bi + regB * bj * bj;

						// update quf
						for (f = 0; f < numFactors; f++) {
							quf = Q.get(u, f);

							delta = eij * (wj * sum_jf[f] - wi * sum_if[f]) + regU * quf;
							QS.add(u, f, -lRate * delta);

							loss += regU * quf * quf;
						}

						// update pvf for v in Ci
						for (f = 0; f < numFactors; f++) {
							for (VectorEntry vk : Ci) {
								v = vk.index();
								if (v != u) {
									pvf = P.get(v, f);
									delta = eij * wi * Q.get(u, f) - regU * pvf;
									PS.add(v, f, lRate * delta);

									loss -= regU * pvf * pvf;
								}
							}
						}

						// update pvf for v in Cj
						for (f = 0; f < numFactors; f++) {
							for (VectorEntry vk : Cj) {
								v = vk.index();
								pvf = P.get(v, f);
								delta = eij * wj * Q.get(u, f) - regU * pvf;
								PS.add(v, f, -lRate * delta);

								loss += regU * pvf * pvf;
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

		return itemBias.get(i) + wi * sum;
	}

	@Override
	public String toString() {
		return Strings.toString(new Object[] { binThold, rho, alpha, numFactors, initLRate, regU, regB, numIters });
	}
}
