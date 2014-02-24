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
	private DenseVector wlr_j, wlr_t;

	public TrustSVD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "TrustSVD";

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

		userBiases.init(initMean, initStd);
		itemBiases.init(initMean, initStd);

		W = new DenseMatrix(numUsers, numFactors);
		Y = new DenseMatrix(numItems, numFactors);

		W.init(initMean, initStd);
		Y.init(initMean, initStd);

		wlr_t = new DenseVector(numUsers);
		wlr_j = new DenseVector(numItems);

		for (int u = 0; u < numUsers; u++) {
			int count = socialMatrix.columnSize(u);
			wlr_t.set(u, 1.0 / Math.sqrt(count));
		}

		for (int j = 0; j < numItems; j++) {
			int count = trainMatrix.columnSize(j);
			wlr_j.set(j, 1.0 / Math.sqrt(count));
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

				double pred = predict(u, j);
				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				SparseVector ur = trainMatrix.row(u);
				int[] nu = ur.getIndex();
				double w_nu = Math.sqrt(nu.length);

				SparseVector ut = socialMatrix.row(u);
				int[] tu = ut.getIndex();
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double bu = userBiases.get(u);
				double sgd = euj + regU * bu;
				userBiases.add(u, -lRate * sgd);

				loss += regU * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj + regI * bj;
				itemBiases.add(j, -lRate * sgd);

				loss += regI * bj * bj;

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

					double delta_u = euj * qjf + regU * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_ts[f]) + regI * qjf;

					PS.add(u, f, delta_u);
					Q.add(j, f, -lRate * delta_j);

					loss += regU * puf * puf + regI * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double delta_y = euj * qjf / w_nu + regI * yif;
						Y.add(i, f, -lRate * delta_y);

						loss += regI * yif * yif;
					}

					// update wvf
					for (int v : tu) {
						double tvf = W.get(v, f);

						double delta_t = euj * qjf / w_tu + regU * tvf;
						WS.add(v, f, delta_t);

						loss += regU * tvf * tvf;
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

				double csgd = eut * regS;

				for (int f = 0; f < numFactors; f++) {
					PS.add(u, f, csgd * W.get(v, f) + regU * P.get(u, f));
					WS.add(v, f, csgd * P.get(u, f) + regU * W.get(v, f));
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

	protected void buildModel_backup() {
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

				double pred = predict(u, j);
				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				SparseVector ur = trainMatrix.row(u);
				int[] nu = ur.getIndex();
				double w_nu = Math.sqrt(nu.length);

				SparseVector ut = socialMatrix.row(u);
				int[] tu = ut.getIndex();
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double reg_u = 1.0 / w_nu;
				double reg_j = wlr_j.get(j);

				double bu = userBiases.get(u);
				double sgd = euj + regU * reg_u * bu;
				userBiases.add(u, -lRate * sgd);

				loss += regU * reg_u * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj + regI * reg_j * bj;
				itemBiases.add(j, -lRate * sgd);

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

						double reg_tv = wlr_t.get(v);
						double delta_t = euj * qjf / w_tu + regU * reg_tv * tvf;
						WS.add(v, f, delta_t);

						loss += regU * reg_tv * tvf * tvf;
					}
				}
			}

			for (int u = 0; u < numUsers; u++) {
				SparseVector tv = socialMatrix.row(u);
				int count = tv.getCount();
				int[] tvs = tv.getIndex();

				if (count == 0)
					continue;

				double reg = 1.0 / Math.sqrt(count);

				// Pu
				double[] sum_us = new double[numFactors];
				for (int v : tvs) {
					double et = DenseMatrix.rowMult(P, u, W, v) - tv.get(v);
					for (int f = 0; f < numFactors; f++)
						sum_us[f] += et * W.get(v, f);

					loss += regS * et * et;
				}

				for (int f = 0; f < numFactors; f++)
					PS.add(u, f, regS * reg * sum_us[f]);

				// Wv
				for (int v : tvs) {
					double et = DenseMatrix.rowMult(P, u, W, v) - tv.get(v);
					for (int f = 0; f < numFactors; f++)
						WS.add(v, f, regS * et * P.get(u, f));
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

	protected void TrustSVD_backup() {
		for (int iter = 1; iter <= maxIters; iter++) {

			loss = 0;
			errs = 0;
			for (MatrixEntry me : trainMatrix) {

				int u = me.row(); // user
				int j = me.column(); // item

				double ruj = me.get();
				if (ruj <= 0.0)
					continue;

				double pred = predict(u, j);
				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				SparseVector ur = trainMatrix.row(u);
				int[] nu = ur.getIndex();
				double w_nu = Math.sqrt(nu.length);

				SparseVector tr = socialMatrix.row(u);
				int[] tu = tr.getIndex();
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double reg_u = 1.0 / w_nu;
				double reg_j = wlr_j.get(j);

				double bu = userBiases.get(u);
				double sgd = euj + regU * reg_u * bu;
				userBiases.add(u, -lRate * sgd);

				loss += regU * reg_u * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj + regI * reg_j * bj;
				itemBiases.add(j, -lRate * sgd);

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

					P.add(u, f, -lRate * delta_u);
					Q.add(j, f, -lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double reg_yj = wlr_j.get(i);
						double delta_y = euj * qjf / w_nu + regI * reg_yj * yif;
						Y.add(i, f, -lRate * delta_y);

						loss += regI * reg_yj * yif * yif;
					}

					for (int v : tu) {
						double tvf = W.get(v, f);

						double reg_tv = wlr_t.get(v);
						double delta_t = euj * qjf / w_tu + regS * reg_tv * tvf;
						W.add(v, f, -lRate * delta_t);

						loss += regS * reg_tv * tvf * tvf;
					}
				}

			}

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