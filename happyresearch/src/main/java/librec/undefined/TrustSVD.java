package librec.undefined;

import java.util.ArrayList;
import java.util.List;

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

	private DenseMatrix Tr, Te, Y;
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

		Tr = new DenseMatrix(numUsers, numFactors);
		Te = new DenseMatrix(numUsers, numFactors);
		Y = new DenseMatrix(numItems, numFactors);

		Tr.init(initMean, initStd);
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
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);
			DenseMatrix YS = new DenseMatrix(numItems, numFactors);
			DenseMatrix TS = new DenseMatrix(numUsers, numFactors);

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
						sum += Tr.get(v, f);

					sum_ts[f] = w_tu > 0 ? sum / w_tu : sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf - regU * reg_u * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_ts[f]) - regI * reg_j * qjf;

					PS.add(u, f, lRate * delta_u);
					QS.add(j, f, lRate * delta_j);

					loss += regU * reg_u * puf * puf + regI * reg_j * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);

						double wlr_yj = wlr ? wlr_j.get(i) : 1.0;
						double delta_y = euj * qjf / w_nu - regI * wlr_yj * yif;
						YS.add(i, f, lRate * delta_y);

						loss += regI * wlr_yj * yif * yif;
					}

					// update wvf
					for (int v : tu) {
						double tvf = Tr.get(v, f);

						double wlr_tv = wlr ? wlr_s.get(v) : 1.0;
						double delta_t = euj * qjf / w_tu - regS * wlr_tv * tvf;
						TS.add(v, f, lRate * delta_t);

						loss += regS * wlr_tv * tvf * tvf;
					}
				}
			}

			// TODO: regularize pu
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

				for (int f = 0; f < numFactors; f++) {
					double diff = P.get(u, f) - sum_us[f];
					PS.add(u, f, -regS * diff);

					loss += regS * diff * diff;
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

						if (w_vv > 0)
							for (int f = 0; f < numFactors; f++)
								PS.add(u, f, regS * (tvu / w_uv) * (P.get(v, f) - sumDiffs[f] / w_uv));
					}
				}
			}// end of regularize pu

			P = P.add(PS.scale(lRate));
			Q = Q.add(QS.scale(lRate));
			Tr = Tr.add(TS.scale(lRate));
			Y = Y.add(YS.scale(lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training
	}

	private void TrusteeSVD() {
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

				SparseVector tr = socialMatrix.column(u);
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
						sum += Te.get(v, f);

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

						double wlr_yj = wlr ? wlr_j.get(i) : 1.0;
						double delta_y = euj * qjf / w_nu - regI * wlr_yj * yif;
						Y.add(i, f, lRate * delta_y);

						loss += regI * wlr_yj * yif * yif;
					}

					for (int v : tu) {
						double tvf = Te.get(v, f);

						double wlr_tv = wlr ? wlr_s.get(v) : 1.0;
						double delta_t = euj * qjf / w_tu - regS * wlr_tv * tvf;
						Te.add(v, f, lRate * delta_t);

						loss += regS * wlr_tv * tvf * tvf;
					}
				}

			}

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
						sum += Tr.get(v, f);

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
						double tvf = Tr.get(v, f);

						double reg_tv = wlr ? wlr_s.get(v) : 1.0;
						double delta_t = euj * qjf / w_tu - regS * reg_tv * tvf;
						Tr.add(v, f, lRate * delta_t);

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

	private void TrTeSVD() {
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
				int[] tru = tr.getIndex();
				double w_tru = Math.sqrt(tru.length);

				SparseVector te = socialMatrix.column(u);
				int[] teu = te.getIndex();
				double w_teu = Math.sqrt(teu.length);

				// update factors
				double bu = userBiases.get(u);
				double sgd = euj - regU * bu;
				userBiases.add(u, lRate * sgd);

				loss += regU * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj - regI * bj;
				itemBiases.add(j, lRate * sgd);

				loss += regI * bj * bj;

				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int i : nu)
						sum += Y.get(i, f);

					sum_ys[f] = w_nu > 0 ? sum / w_nu : sum;
				}

				double[] sum_trs = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int v : tru)
						sum += Tr.get(v, f);

					sum_trs[f] = w_tru > 0 ? sum / w_tru : sum;
				}

				double[] sum_tes = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int k : tru)
						sum += Te.get(k, f);

					sum_tes[f] = w_teu > 0 ? sum / w_teu : sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf - regU * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_trs[f] + sum_tes[f]) - regI * qjf;

					P.add(u, f, lRate * delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * puf * puf + regI * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);
						double delta_y = euj * qjf / w_nu - regI * yif;
						Y.add(i, f, lRate * delta_y);

						loss += regI * yif * yif;
					}

					for (int v : tru) {
						double tvf = Tr.get(v, f);
						double delta_t = euj * qjf / w_tru - regS * tvf;
						Tr.add(v, f, lRate * delta_t);

						loss += regS * tvf * tvf;
					}

					for (int k : teu) {
						double tkf = Te.get(k, f);
						double delta_t = euj * qjf / w_teu - regS * tkf;
						Te.add(k, f, lRate * delta_t);

						loss += regS * tkf * tkf;
					}
				}

			}

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;

		}// end of training
	}

	private void TrTeSVD2() {
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
				SparseVector te = socialMatrix.column(u);

				List<Integer> tns = new ArrayList<>();
				for (int v : tr.getIndex()) {
					if (!tns.contains(v))
						tns.add(v);
				}

				for (int k : te.getIndex()) {
					if (!tns.contains(k))
						tns.add(k);
				}

				double w_tn = Math.sqrt(tns.size());

				// update factors
				double bu = userBiases.get(u);
				double sgd = euj - regU * bu;
				userBiases.add(u, lRate * sgd);

				loss += regU * bu * bu;

				double bj = itemBiases.get(j);
				sgd = euj - regI * bj;
				itemBiases.add(j, lRate * sgd);

				loss += regI * bj * bj;

				double[] sum_ys = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int i : nu)
						sum += Y.get(i, f);

					sum_ys[f] = w_nu > 0 ? sum / w_nu : sum;
				}

				double[] sum_tns = new double[numFactors];
				for (int f = 0; f < numFactors; f++) {
					double sum = 0;
					for (int v : tns)
						sum += Tr.get(v, f);

					sum_tns[f] = w_tn > 0 ? sum / w_tn : sum;
				}

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					double delta_u = euj * qjf - regU * puf;
					double delta_j = euj * (puf + sum_ys[f] + sum_tns[f]) - regI * qjf;

					P.add(u, f, lRate * delta_u);
					Q.add(j, f, lRate * delta_j);

					loss += regU * puf * puf + regI * qjf * qjf;

					for (int i : nu) {
						double yif = Y.get(i, f);
						double delta_y = euj * qjf / w_nu - regI * yif;
						Y.add(i, f, lRate * delta_y);

						loss += regI * yif * yif;
					}

					for (int v : tns) {
						double tvf = Tr.get(v, f);
						double delta_t = euj * qjf / w_tn - regS * tvf;
						Tr.add(v, f, lRate * delta_t);

						loss += regS * tvf * tvf;
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
		switch (model) {
		case "Tr":
			TrusterSVD();
			break;
		case "Te":
			TrusteeSVD();
			break;
		case "TrTe":
			TrTeSVD2();
			break;
		case "T":
		default:
			TrTeSVD();
			break;
		}
	}

	private double predictTr(int u, int j) {

		double pred = 0.0;
		SparseVector tr = socialMatrix.row(u);
		int count = tr.getCount();

		if (count > 0) {
			double sum = 0.0;
			for (int v : tr.getIndex())
				sum += DenseMatrix.rowMult(Tr, v, Q, j);

			pred = sum / Math.sqrt(count);
		}

		return pred;
	}

	private double predictTe(int u, int j) {

		double pred = 0.0;
		SparseVector te = socialMatrix.column(u);
		int count = te.getCount();

		if (count > 0) {
			double sum = 0.0;
			for (int w : te.getIndex())
				sum += DenseMatrix.rowMult(Te, w, Q, j);

			pred = sum / Math.sqrt(count);
		}

		return pred;
	}

	private double predictTrTe2(int u, int j) {

		double pred = 0.0;
		SparseVector tr = socialMatrix.row(u);
		SparseVector te = socialMatrix.column(u);

		List<Integer> tns = new ArrayList<>();
		for (int v : tr.getIndex())
			if (!tns.contains(v))
				tns.add(v);

		for (int k : te.getIndex())
			if (!tns.contains(k))
				tns.add(k);

		int count = tns.size();

		if (count > 0) {
			double sum = 0.0;
			for (int w : tns)
				sum += DenseMatrix.rowMult(Tr, w, Q, j);

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

		// T
		switch (model) {
		case "Tr":
			pred += predictTr(u, j);
			break;
		case "Te":
			pred += predictTe(u, j);
			break;
		case "TrTe":
			pred += predictTrTe2(u, j);
			break;
		case "T":
		default:
			pred += predictTr(u, j) + predictTe(u, j);
			break;
		}

		return pred;
	}

	@Override
	public String toString() {
		return super.toString() + "," + wlr;
	}

}