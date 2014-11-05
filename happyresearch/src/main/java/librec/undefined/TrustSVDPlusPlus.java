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
	private float alpha;

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
	}

	protected void buildModel() throws Exception {
		for (int iter = 1; iter <= numIters; iter++) {
			loss = 0;
			errs = 0;

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			// DenseMatrix QS = new DenseMatrix(numItems, numFactors);
			DenseMatrix WS = new DenseMatrix(numUsers, numFactors);

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
					for (int v : tuc)
						sum += DenseMatrix.rowMult(W, v, Q, j);

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
					for (int v : tuc)
						sum += W.get(v, f);

					sum_tcs[f] = reg_uc * sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf + regU * reg_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + alpha * sum_trs[f] + (1 - alpha) * sum_tcs[f]) + regI
							* reg_j * qjf;

					PS.add(u, f, delta_u);
					Q.add(j, f, -lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					// update Y
					for (int i : Iu) {
						double yif = Y.get(i, f);
						double reg_i = wlr_j.get(i);

						double delta_y = euj * qjf * reg_u + regI * reg_i * yif;
						Y.add(i, f, -lRate * delta_y);

						loss += regI * reg_i * yif * yif;
					}

					double delta = 1 - alpha > 0 ? 1.0 : 0.0;

					// update tur
					for (int v : tur) {
						double wvf = W.get(v, f);
						double sigma = tc.contains(v) ? 1.0 : 0.0;

						double reg_vc = wlr_tc.get(v);
						double reg_vr = wlr_tr.get(v);

						double delta_t = euj * qjf * (alpha * reg_ur + sigma * (1 - alpha) * reg_uc) + regU
								* (reg_vc + sigma * delta * reg_vr) * wvf;
						WS.add(v, f, delta_t);

						loss += regU * (reg_vc + delta * reg_vr) * wvf * wvf;
					}

					// update tuc
					if (delta > 0) {
						for (int v : tuc) {
							double wvf = W.get(v, f);
							double sigma = tr.contains(v) ? 1.0 : 0.0;

							double reg_vr = wlr_tr.get(v);
							double reg_vc = wlr_tc.get(v);

							double delta_t = euj * qjf * (sigma * alpha * reg_ur + (1 - alpha) * reg_uc) + regU
									* (sigma * reg_vc + reg_vr) * wvf;
							WS.add(v, f, delta_t);
						}
					}
				}
			}

			for (MatrixEntry me : socialMatrix) {
				int u = me.row();
				int v = me.column();
				double tuv = me.get();
				if (tuv == 0)
					continue;

				double pred = DenseMatrix.rowMult(P, u, W, v);
				double euv = pred - tuv;

				loss += regS * euv * euv;

				double reg_ur = wlr_tr.get(u);
				double reg_uc = wlr_tc.get(u);

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double wvf = W.get(v, f);

					PS.add(u, f, regS * (euv * wvf + alpha * reg_ur + (1 - alpha) * reg_uc) * puf);
					WS.add(v, f, regS * euv * puf);

					loss += regS * (alpha * reg_ur + (1 - alpha) * reg_uc) * puf * puf;
				}
			}

			P = P.add(PS.scale(-lRate));
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
			for (int v : tc.getIndex())
				sum += DenseMatrix.rowMult(W, v, Q, j);

			pred += (1 - alpha) * (sum / Math.sqrt(tc.getCount()));
		}

		return pred;
	}

	@Override
	public String toString() {
		return super.toString() + "," + alpha;
	}
}