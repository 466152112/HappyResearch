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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import librec.data.DenseMatrix;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.SocialRecommender;

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
		beta = 0.01;

	}

	protected double similarity(Integer u, Integer v) {
		if (userCorrs.contains(u, v))
			return userCorrs.get(u, v);

		if (userCorrs.contains(v, u))
			return userCorrs.get(v, u);

		double sim = 0;

		if (u < trainMatrix.numRows() && v < trainMatrix.numRows()) {
			SparseVector uv = trainMatrix.row(u);
			if (uv.getCount() > 0) {
				SparseVector vv = trainMatrix.row(v);
				sim = correlation(uv, vv, "pcc");
			}
		}

		sim = Double.isNaN(sim) ? 0 : (1.0 + sim) / 2;
		userCorrs.put(u, v, sim);

		return sim;
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {
			// temporary data
			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
			DenseMatrix QS = new DenseMatrix(numItems, numFactors);

			errs = 0;
			loss = 0;

			// ratings
			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int j = me.column();
				double rate = me.get();
				if (rate <= 0)
					continue;

				double pred = predict(u, j);
				double euj = pred - rate;

				errs += euj * euj;
				loss += euj * euj;

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);
					PS.add(u, f, euj * qjf + regU * puf);
					QS.add(j, f, euj * puf + regI * qjf);

					loss += regU * puf * puf;
					loss += regI * qjf * qjf;
				}
			}

			// friends
			for (int u = 0; u < numUsers; u++) {
				// out links
				SparseVector uos = socialMatrix.row(u);
				double weight = uos.getCount();
				for (int k : uos.getIndex()) {
					double suk = similarity(u, k);

					for (int f = 0; f < numFactors; f++) {
						double euk = P.get(u, f) - P.get(k, f);
						PS.add(u, f, beta * suk * euk / weight);

						loss += beta * suk * euk * euk / weight;
					}
				}

				// in links
				SparseVector uis = socialMatrix.column(u);
				weight = uis.getCount();

				for (int g : uis.getIndex()) {
					double sug = similarity(u, g);

					for (int f = 0; f < numFactors; f++) {
						double eug = P.get(u, f) - P.get(g, f);
						PS.add(u, f, beta * sug * eug / weight);
					}
				}
			} // end of for loop

			P = P.add(PS.scale(-lRate));
			Q = Q.add(QS.scale(-lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

}
