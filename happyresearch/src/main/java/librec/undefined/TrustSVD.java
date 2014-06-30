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
public class TrustSVD extends SocialRecommender {

	private DenseMatrix W, Y;
	private DenseVector wlr_j, wlr_tc, wlr_tr;

	public TrustSVD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		if (params.containsKey("val.reg")) {
			double reg = RecUtils.getMKey(params, "val.reg");

			regU = reg;
			regI = reg;
			regS = reg;
		} else {
			regS = RecUtils.getMKey(params, "val.reg.social");
		}

	}

	@Override
	protected void initModel() {
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

	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {
			loss = 0;
			errs = 0;

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix WS = new DenseMatrix(numUsers, numFactors);

			for (MatrixEntry me : trainMatrix) {
				int u = me.row(); // user
				int j = me.column(); // item

				double ruj = me.get();
				if (ruj <= 0.0)
					continue;

				//double pred = predict(u, j);
				
				// To speed up, directly access the prediction
				double bu = userBiases.get(u);
				double bj = itemBiases.get(j);
				double pred = globalMean + bu + bj + DenseMatrix.rowMult(P, u, Q, j);

				// Y
				SparseVector uv = trainMatrix.row(u);
				int[] nu = uv.getIndex();
				if (uv.getCount() > 0) {
					double sum = 0;
					for (int i : nu)
						sum += DenseMatrix.rowMult(Y, i, Q, j);

					pred += sum / Math.sqrt(uv.getCount());
				}

				// W
				SparseVector tr = socialMatrix.row(u);
				int[] tu = tr.getIndex();
				if (tr.getCount() > 0) {
					double sum = 0.0;
					for (int v : tu)
						sum += DenseMatrix.rowMult(W, v, Q, j);

					pred += sum / Math.sqrt(tr.getCount());
				}
				
				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				double w_nu = Math.sqrt(nu.length);
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double reg_u = 1.0 / w_nu;
				double reg_j = wlr_j.get(j);

				double sgd = euj + regU * reg_u * bu;
				userBiases.add(u, -lRate * sgd);

				sgd = euj + regI * reg_j * bj;
				itemBiases.add(j, -lRate * sgd);

				loss += regU * reg_u * bu * bu;
				loss += regI * reg_j * bj * bj;

				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int i : nu)
						sum += Y.get(i, f);

					sum_ys[f] = w_nu > 0 ? sum / w_nu : sum;
				}

				double[] sum_ts = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int v : tu)
						sum += W.get(v, f);

					sum_ts[f] = w_tu > 0 ? sum / w_tu : sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf + regU * reg_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_ts[f]) + regI * reg_j * qjf;

					PS.add(u, f, delta_u);
					Q.add(j, f, -lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double reg_yi = wlr_j.get(i);
						double delta_y = euj * qjf / w_nu + regI * reg_yi * yif;
						Y.add(i, f, -lRate * delta_y);

						loss += regI * reg_yi * yif * yif;
					}

					// update wvf
					for (int v : tu) {
						double tvf = W.get(v, f);

						double reg_v = wlr_tc.get(v);
						double delta_t = euj * qjf / w_tu + regU * reg_v * tvf;
						WS.add(v, f, delta_t);

						loss += regU * reg_v * tvf * tvf;
					}
				}
			}

			for (MatrixEntry me : socialMatrix) {
				int u = me.row();
				int v = me.column();
				double tuv = me.get();
				if (tuv <= 0)
					continue;

				double pred = DenseMatrix.rowMult(P, u, W, v);
				double eut = pred - tuv;

				loss += regS * eut * eut;

				double csgd = regS * eut;
				double reg_u = wlr_tr.get(u);

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double wvf = W.get(v, f);

					PS.add(u, f, csgd * wvf + regS * reg_u * puf);
					WS.add(v, f, csgd * puf);

					loss += regS * reg_u * puf * puf;
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

		// W
		SparseVector tr = socialMatrix.row(u);
		if (tr.getCount() > 0) {
			double sum = 0.0;
			for (int v : tr.getIndex())
				sum += DenseMatrix.rowMult(W, v, Q, j);

			pred += sum / Math.sqrt(tr.getCount());
		}

		return pred;
	}
}