package librec.undefined;

import happy.coding.system.Debug;
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

	private DenseMatrix W, Te, Y;
	private String model;
	private boolean wlr = false;

	private DenseVector wlr_j, wlr_s;

	public TrustSVD(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		model = cf.getString("TrustSVD.model");
		algoName = "TrustSVD (" + model + ")";

		wlr = cf.isOn("TrustSVD.wlr");

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
		Te = new DenseMatrix(numUsers, numFactors);
		Y = new DenseMatrix(numItems, numFactors);

		W.init(initMean, initStd);
		Te.init(initMean, initStd);
		Y.init(initMean, initStd);

		if (wlr) {
			wlr_s = new DenseVector(numUsers);
			wlr_j = new DenseVector(numItems);

			for (int u = 0; u < numUsers; u++) {
				int count = model.equals("Tr") ? socialMatrix.columnSize(u) : socialMatrix.rowSize(u);
				wlr_s.set(u, 1.0 / Math.sqrt(count));
			}

			for (int j = 0; j < numItems; j++) {
				int count = trainMatrix.columnSize(j);
				wlr_j.set(j, 1.0 / Math.sqrt(count));
			}
		}
	}

	private void TrusterSVD() {
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
				double euj = ruj - pred;

				errs += euj * euj;
				loss += euj * euj;

				SparseVector ur = trainMatrix.row(u);
				int[] nu = ur.getIndex();
				double w_nu = Math.sqrt(nu.length);

				SparseVector ut = socialMatrix.row(u);
				int[] tu = ut.getIndex();
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double reg_u = wlr ? 1.0 / w_nu : 1.0;
				double reg_j = wlr ? wlr_j.get(j) : 1.0;

				double bu = userBiases.get(u);
				double sgd = euj - regU * reg_u * bu;
				userBiases.add(u, lRate * sgd);

				loss += regU * reg_u * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj - regI * reg_j * bj;
				itemBiases.add(j, lRate * sgd);

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

					double delta_u = euj * qjf - regU * reg_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_ts[f]) - regI * reg_j * qjf;

					PS.add(u, f, delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double reg_yj = wlr ? wlr_j.get(i) : 1.0;
						double delta_y = euj * qjf / w_nu - regI * reg_yj * yif;
						Y.add(i, f, lRate * delta_y);

						loss += regI * reg_yj * yif * yif;
					}

					// update wvf
					for (int v : tu) {
						double tvf = W.get(v, f);

						double reg_tv = wlr ? wlr_s.get(v) : 1.0;
						double delta_t = euj * qjf / w_tu - regU * reg_tv * tvf;
						WS.add(v, f, delta_t);

						loss += regU * reg_tv * tvf * tvf;
					}
				}
			}

			// TODO: regularize pu
			if (Debug.OFF) {
				for (int u = 0; u < numUsers; u++) {

					SparseVector ur = socialMatrix.row(u);
					int[] tu = ur.getIndex();
					int cnt = ur.getCount();
					if (cnt == 0)
						continue;

					double w_tu = Math.sqrt(cnt);

					double[] sum_us = new double[numFactors];
					for (int f = 0; f < numFactors; f++) {
						double sum = 0;
						for (int v : tu)
							sum += P.get(v, f) * socialMatrix.get(u, v);

						sum_us[f] = w_tu > 0 ? sum / w_tu : sum;
					}

					double reg_u = 1.0;
					if (wlr && u < trainMatrix.numRows()) {
						SparseVector rs = trainMatrix.row(u);
						if (rs.getCount() > 0)
							reg_u = 1.0 / Math.sqrt(rs.getCount());
					}

					for (int f = 0; f < numFactors; f++) {
						double diff = P.get(u, f) - sum_us[f];
						PS.add(u, f, -regS * reg_u * diff);

						loss += regS * reg_u * diff * diff;
					}

					SparseVector uvec = socialMatrix.column(u);
					double w_uv = Math.sqrt(uvec.getCount());
					for (int v : uvec.getIndex()) {
						double tvu = socialMatrix.get(v, u);

						SparseVector vvec = socialMatrix.row(v);
						double w_vv = Math.sqrt(vvec.getCount());
						double[] sumDiffs = new double[numFactors];

						for (int w : vvec.getIndex()) {
							for (int f = 0; f < numFactors; f++)
								sumDiffs[f] += socialMatrix.get(v, w) * P.get(w, f);
						}

						if (w_vv > 0)
							for (int f = 0; f < numFactors; f++)
								PS.add(u, f, regS * reg_u * (tvu / w_uv) * (P.get(v, f) - sumDiffs[f] / w_vv));
					}
				}// end of regularized pu
			} else if (Debug.ON) {
				for (int u = 0; u < numUsers; u++) {
					SparseVector tv = socialMatrix.row(u);
					int count = tv.getCount();
					int[] tvs = tv.getIndex();

					// Pu
					double[] sum_us = new double[numFactors];
					for (int v : tvs) {
						double et = tv.get(v) - DenseMatrix.rowMult(P, u, W, v);
						for (int f = 0; f < numFactors; f++)
							sum_us[f] += et * W.get(v, f);

						loss += regS * et * et;
					}

					for (int f = 0; f < numFactors; f++) {
						double val = count > 0 ? sum_us[f] / count : sum_us[f];
						PS.add(u, f, regS * val);
					}

					// Wv
					for (int v : tvs) {
						double et = tv.get(v) - DenseMatrix.rowMult(P, u, W, v);
						for (int f = 0; f < numFactors; f++)
							WS.add(v, f, et * regS * P.get(u, f));
					}
				}
			}

			P = P.add(PS.scale(lRate));
			W = W.add(WS.scale(lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training
	}

	protected void TrusterSVD_worked_backup() {
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
				double euj = ruj - pred;

				errs += euj * euj;
				loss += euj * euj;

				SparseVector ur = trainMatrix.row(u);
				int[] nu = ur.getIndex();
				double w_nu = Math.sqrt(nu.length);

				SparseVector tr = socialMatrix.row(u);
				int[] tu = tr.getIndex();
				double w_tu = Math.sqrt(tu.length);

				// update factors
				double reg_u = wlr ? 1.0 / w_nu : 1.0;
				double reg_j = wlr ? wlr_j.get(j) : 1.0;

				double bu = userBiases.get(u);
				double sgd = euj - regU * reg_u * bu;
				userBiases.add(u, lRate * sgd);

				loss += regU * reg_u * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj - regI * reg_j * bj;
				itemBiases.add(j, lRate * sgd);

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

					double delta_u = euj * qjf - regU * reg_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_ts[f]) - regI * reg_j * qjf;

					P.add(u, f, lRate * delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double reg_yj = wlr ? wlr_j.get(i) : 1.0;
						double delta_y = euj * qjf / w_nu - regI * reg_yj * yif;
						Y.add(i, f, lRate * delta_y);

						loss += regI * reg_yj * yif * yif;
					}

					for (int v : tu) {
						double tvf = W.get(v, f);

						double reg_tv = wlr ? wlr_s.get(v) : 1.0;
						double delta_t = euj * qjf / w_tu - regS * reg_tv * tvf;
						W.add(v, f, lRate * delta_t);

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
	protected void buildModel() {
		TrusterSVD();
	}

	private double predictTr(int u, int j) {

		double pred = 0.0;
		SparseVector tr = socialMatrix.row(u);
		int count = tr.getCount();

		if (count > 0) {
			double sum = 0.0;
			for (int v : tr.getIndex())
				sum += DenseMatrix.rowMult(W, v, Q, j);

			pred = sum / Math.sqrt(count);
		}

		return pred;
	}

	@Override
	protected double predict(int u, int j) {
		double pred = globalMean + userBiases.get(u) + itemBiases.get(j) + DenseMatrix.rowMult(P, u, Q, j);

		// Y
		SparseVector uv = trainMatrix.row(u);
		if (uv.getCount() > 0) {
			double sum = 0;
			for (int k : uv.getIndex())
				sum += DenseMatrix.rowMult(Y, k, Q, j);

			pred += sum / Math.sqrt(uv.getCount());
		}

		// W
		pred += predictTr(u, j);

		return pred;
	}

	@Override
	public String toString() {
		return super.toString() + "," + wlr;
	}

}