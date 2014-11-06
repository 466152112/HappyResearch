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

import librec.data.DenseMatrix;
import librec.data.DenseVector;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.intf.SocialRecommender;

/**
 * Our ongoing testing algorithm
 * 
 * @author guoguibing
 * 
 */
public class TrustSVDPlusPlus extends SocialRecommender {

	private DenseMatrix W, Y;
	private DenseVector wlr_j, wlr_tc, wlr_tr;
	private float alpha = -1;

	double delta_a, delta_1_a;

	public TrustSVDPlusPlus(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		if (params.containsKey("val.reg")) {
			double reg = RecUtils.getMKey(params, "val.reg");

			regB = (float) reg;
			regU = (float) reg;
			regI = (float) reg;
			regS = (float) reg;
		} else if (params.containsKey("val.reg.social")) {
			regS = (float) RecUtils.getMKey(params, "val.reg.social");
		} else if (params.containsKey("TrustSVD++.alpha")) {
			alpha = (float) RecUtils.getMKey(params, "TrustSVD++.alpha");
		}

		if (alpha < 0)
			alpha = cf.getFloat("TrustSVD++.alpha");

		algoName = "TrustSVD++";
	}

	@Override
	protected void initModel() throws Exception {
		super.initModel();

		userBiases = new DenseVector(numUsers);
		itemBiases = new DenseVector(numItems);

		W = new DenseMatrix(numUsers, numFactors);
		Y = new DenseMatrix(numItems, numFactors);

		if (initByNorm) {
			userBiases.init(initMean, initStd);
			itemBiases.init(initMean, initStd);
			W.init(initMean, initStd);
			Y.init(initMean, initStd);

		} else {
			userBiases.init();
			itemBiases.init();
			W.init();
			Y.init();
		}

		// weighted lambda regularization (wlr)
		wlr_tc = new DenseVector(numUsers);
		wlr_tr = new DenseVector(numUsers);
		wlr_j = new DenseVector(numItems);

		for (int u = 0; u < numUsers; u++) {
			int count = socialMatrix.columnSize(u);
			wlr_tc.set(u, count > 0 ? 1.0 / Math.sqrt(count) : 1.0);

			count = socialMatrix.rowSize(u);
			wlr_tr.set(u, count > 0 ? 1.0 / Math.sqrt(count) : 1.0);
		}

		for (int j = 0; j < numItems; j++) {
			int count = trainMatrix.columnSize(j);
			wlr_j.set(j, count > 0 ? 1.0 / Math.sqrt(count) : 1.0);
		}

		delta_a = alpha > 0 ? 1.0 : 0.0;
		delta_1_a = 1 - alpha > 0 ? 1.0 : 0.0;
	}

	protected void buildModel() throws Exception {
		for (int iter = 1; iter <= numIters; iter++) {
			loss = 0;
			errs = 0;

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);
			DenseMatrix WS = new DenseMatrix(numUsers, numFactors);

			// ratings
			for (MatrixEntry me : trainMatrix) {
				int u = me.row(); // user
				int j = me.column(); // item

				double ruj = me.get();
				if (ruj <= 0.0)
					continue;

				// To speed up, directly access the prediction
				double bu = userBiases.get(u), bj = itemBiases.get(j);
				double pred = globalMean + bu + bj + DenseMatrix.rowMult(P, u, Q, j);

				// Y
				SparseVector ru = trainMatrix.row(u); // row u
				int[] Iu = ru.getIndex(); // rated items
				if (ru.getCount() > 0) {
					double sum = 0;
					for (int i : Iu)
						sum += DenseMatrix.rowMult(Y, i, Q, j);

					pred += sum / Math.sqrt(ru.getCount());
				}

				// Tur
				SparseVector tr = socialMatrix.row(u); // trustees of user u
				int[] tur = tr.getIndex();
				if (tr.getCount() > 0) {
					double sum = 0.0;
					for (int v : tur)
						sum += DenseMatrix.rowMult(W, v, Q, j);

					pred += alpha * (sum / Math.sqrt(tr.getCount()));
				}

				// Tuc
				SparseVector tc = socialMatrix.column(u); // trusters of user u
				int[] tuc = tc.getIndex();
				if (tc.getCount() > 0) {
					double sum = 0.0;
					for (int k : tuc)
						sum += DenseMatrix.rowMult(W, k, Q, j);

					pred += (1 - alpha) * (sum / Math.sqrt(tc.getCount()));
				}

				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				// update factors
				double reg_u = Iu.length > 0 ? 1.0 / Math.sqrt(Iu.length) : 1.0;
				double reg_ur = wlr_tr.get(u);
				double reg_uc = wlr_tc.get(u);
				double reg_j = wlr_j.get(j);

				double sgd = euj + regB * reg_u * bu;
				userBiases.add(u, -lRate * sgd);

				sgd = euj + regB * reg_j * bj;
				itemBiases.add(j, -lRate * sgd);

				loss += regB * reg_u * bu * bu;
				loss += regB * reg_j * bj * bj;

				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int i : Iu)
						sum += Y.get(i, f);

					sum_ys[f] = reg_u * sum;
				}

				double[] sum_trs = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int v : tur)
						sum += W.get(v, f);

					sum_trs[f] = reg_ur * sum;
				}

				double[] sum_tcs = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int k : tuc)
						sum += P.get(k, f);

					sum_tcs[f] = reg_uc * sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double sgd_u = regU * reg_u + regS * (alpha * reg_ur + (1 - alpha) * reg_uc);
					double delta_u = euj * qjf + sgd_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + alpha * sum_trs[f] + (1 - alpha) * sum_tcs[f]) + regI
							* reg_j * qjf;

					PS.add(u, f, delta_u);
					QS.add(j, f, delta_j);

					loss += sgd_u * puf * puf + regI * reg_j * qjf * qjf;

					// update Y
					for (int i : Iu) {
						double yif = Y.get(i, f);
						double reg_yi = wlr_j.get(i);

						double delta_y = euj * reg_u * qjf + regI * reg_yi * yif;
						Y.add(i, f, -lRate * delta_y);

						loss += regI * reg_yi * yif * yif;
					}

					// update W
					for (int v : tur) {
						double wvf = W.get(v, f);
						double reg_vc = wlr_tr.get(v);

						double sgd_v = regU * delta_a * reg_vc;
						double delta_v = euj * alpha * reg_ur * qjf + sgd_v * wvf;
						WS.add(v, f, delta_v);

						loss += sgd_v * wvf * wvf;
					}

					// update Pkf
					for (int k : tuc) {
						double pkf = P.get(k, f);
						double reg_kr = wlr_tc.get(k);

						double sgd_k = regU * delta_1_a * reg_kr;
						double delta_k = euj * (1 - alpha) * reg_uc * qjf + sgd_k * pkf;
						PS.add(k, f, delta_k);

						loss += sgd_k * pkf * pkf;
					}
				}
			}

			// trust
			for (int u = 0; u < numUsers; u++) {
				SparseVector tr = socialMatrix.row(u);
				SparseVector tc = socialMatrix.column(u);

				for (int f = 0; f < numFactors; f++) {
					// wvf
					for (VectorEntry ve : tr) {
						int v = ve.index();
						double tuv = ve.get();
						double puv = DenseMatrix.rowMult(P, u, W, v);
						double euv = puv - tuv;

						double cmg = regS * alpha;
						PS.add(u, f, cmg * euv * W.get(v, f));
						WS.add(v, f, cmg * euv * P.get(u, f));

						loss += cmg * euv * euv;
					}

					// pkf
					for (VectorEntry ve : tc) {
						int k = ve.index();
						double tku = ve.get();
						double pku = DenseMatrix.rowMult(P, k, W, u);
						double eku = pku - tku;

						double cmg = regS * (1 - alpha);
						PS.add(k, f, cmg * eku * W.get(u, f));

						loss += cmg * eku * eku;
					}
				}
			}

			P = P.add(PS.scale(-lRate));
			Q = Q.add(QS.scale(-lRate));
			W = W.add(WS.scale(-lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training
	}

	@Override
	protected double predict(int u, int j) {
		double pred = globalMean + userBiases.get(u) + itemBiases.get(j) + DenseMatrix.rowMult(P, u, Q, j);

		// Y
		SparseVector uv = trainMatrix.row(u);
		if (uv.getCount() > 0) {
			double sum = 0;
			for (int i : uv.getIndex())
				sum += DenseMatrix.rowMult(Y, i, Q, j);

			pred += sum / Math.sqrt(uv.getCount());
		}

		// Tur: Tu row
		SparseVector tr = socialMatrix.row(u);
		if (tr.getCount() > 0) {
			double sum = 0.0;
			for (int v : tr.getIndex())
				sum += DenseMatrix.rowMult(W, v, Q, j);

			pred += alpha * (sum / Math.sqrt(tr.getCount()));
		}

		// Tuc: Tu column
		SparseVector tc = socialMatrix.column(u);
		if (tc.getCount() > 0) {
			double sum = 0.0;
			for (int k : tc.getIndex())
				sum += DenseMatrix.rowMult(P, k, Q, j);

			pred += (1 - alpha) * (sum / Math.sqrt(tc.getCount()));
		}

		return pred;
	}

	@Override
	public String toString() {
		return super.toString() + "," + alpha;
	}
}