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
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.SocialRecommender;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Hao Ma, Dengyong Zhou, Chao Liu, Michael R. Lyu and Irwin King,
 * <strong>Recommender systems with social regularization</strong>, WSDM 2011.<br/>
 * 
 * In the original paper, this method is named as "SR2_pcc". For consistency, we
 * rename it as "SoReg".
 * 
 * @author guoguibing
 * 
 */
public class SoReg extends SocialRecommender {

	private Table<Integer, Integer, Double> userCorrs;
	private double beta;

	public SoReg(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "SoReg";
		initByNorm = false;
	}

	@Override
	protected void initModel() {
		super.initModel();

		userCorrs = HashBasedTable.create();
		beta = 0.001;
	}

	protected double similarity(Integer u, Integer v) {
		if (userCorrs.contains(u, v))
			return userCorrs.get(u, v);

		if (userCorrs.contains(v, u))
			return userCorrs.get(v, u);

		double sim = Double.NaN;

		if (u < trainMatrix.numRows() && v < trainMatrix.numRows()) {
			SparseVector uv = trainMatrix.row(u);
			if (uv.getCount() > 0) {
				SparseVector vv = trainMatrix.row(v);
				sim = correlation(uv, vv, "pcc");

				if (!Double.isNaN(sim))
					sim = (1.0 + sim) / 2;
			}
		}

		userCorrs.put(u, v, sim);

		return sim;
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {

			errs = 0;
			loss = 0;

			// temporary data
			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);

			// ratings
			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int j = me.column();
				double ruj = me.get();
				if (ruj <= 0)
					continue;

				double pred = predict(u, j);
				double euj = pred - ruj;

				errs += euj * euj;
				loss += euj * euj;

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					PS.add(u, f, euj * qjf + regU * puf);
					Q.add(j, f, -lRate * (euj * puf + regI * qjf));

					loss += regU * puf * puf;
					loss += regI * qjf * qjf;
				}
			}

			// friends
			for (int u = 0; u < numUsers; u++) {
				// out links: F+
				SparseVector uos = socialMatrix.row(u);

				double[] sumF = new double[numFactors];
				int count = 0;
				for (int k : uos.getIndex()) {
					double suk = similarity(u, k);
					if (Double.isNaN(suk))
						continue;

					count++;

					for (int f = 0; f < numFactors; f++) {
						double euk = P.get(u, f) - P.get(k, f);
						//PS.add(u, f, beta * suk * euk);
						sumF[f] += beta * suk * euk;

						loss += beta * suk * euk * euk;
					}
				}

				if (count > 0)
					for (int f = 0; f < numFactors; f++)
						PS.add(u, f, sumF[f] / count);

				// in links: F-
				SparseVector uis = socialMatrix.column(u);
				sumF = new double[numFactors];
				count = 0;
				for (int g : uis.getIndex()) {
					double sug = similarity(u, g);
					if (Double.isNaN(sug))
						continue;

					count++;
					for (int f = 0; f < numFactors; f++) {
						double eug = P.get(u, f) - P.get(g, f);
						// PS.add(u, f, beta * sug * eug);
						sumF[f] += beta * sug * eug;
					}
				}

				if (count > 0)
					for (int f = 0; f < numFactors; f++)
						PS.add(u, f, sumF[f] / count);

			} // end of for loop

			P = P.add(PS.scale(-lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

}
