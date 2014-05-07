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

import happy.coding.system.Debug;
import librec.data.DenseMatrix;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.intf.SocialRecommender;

/**
 * Hao Ma, Haixuan Yang, Michael R. Lyu and Irwin King, <strong>SoRec: Social
 * recommendation using probabilistic matrix factorization</strong>, ACM CIKM
 * 2008.
 * 
 * @author guoguibing
 * 
 */
public class SoRec extends SocialRecommender {

	private DenseMatrix Z;
	private double regC, regZ;

	public SoRec(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
		super(trainMatrix, testMatrix, fold);

		algoName = "SoRec";
		initByNorm = false;
	}

	@Override
	protected void initModel() {
		super.initModel();

		Z = new DenseMatrix(numUsers, numFactors);
		Z.init();

		if (Debug.OFF) {
			// suggested settings on Epinions by the paper
			regC = 10;
			regU = 0.001;
			regI = 0.001;
			regZ = 0.001;
		}else{
			regC = cf.getDouble("SoRec.reg.c");
			regZ = cf.getDouble("SoRec.reg.z");
		}
	}

	@Override
	protected void buildModel() {
		for (int iter = 1; iter <= maxIters; iter++) {
			errs = 0;
			loss = 0;

			DenseMatrix PS = new DenseMatrix(numUsers, numFactors);

			// ratings
			for (MatrixEntry me : trainMatrix) {
				int u = me.row();
				int j = me.column();
				double ruj = me.get();
				if (ruj <= 0)
					continue;

				double pred = predict(u, j, false);
				double euj = g(pred) - normalize(ruj);

				errs += euj * euj;
				loss += euj * euj;

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double qjf = Q.get(j, f);

					PS.add(u, f, gd(pred) * euj * qjf + regU * puf);
					Q.add(j, f, -lRate * (gd(pred) * euj * puf + regI * qjf));

					loss += regU * puf * puf + regI * qjf * qjf;
				}
			}

			// friends
			for (MatrixEntry me : socialMatrix) {
				int u = me.row();
				int v = me.column();
				double tuv = me.get();
				if (tuv <= 0)
					continue;

				double pred = DenseMatrix.rowMult(P, u, Z, v);
				double euv = g(pred) - tuv;
				loss += regC * euv * euv;

				for (int f = 0; f < numFactors; f++) {
					double puf = P.get(u, f);
					double zvf = Z.get(v, f);

					PS.add(u, f, regC * gd(pred) * euv * zvf);
					Z.add(v, f, -lRate * (regC * gd(pred) * euv * puf + regZ * zvf));

					loss += regZ * zvf * zvf;
				}
			}

			P = P.add(PS.scale(-lRate));

			errs *= 0.5;
			loss *= 0.5;

			if (isConverged(iter))
				break;
		}
	}

	@Override
	protected double predict(int u, int j, boolean bounded) {
		double pred = DenseMatrix.rowMult(P, u, Q, j);

		if (bounded)
			return denormalize(g(pred));

		return pred;
	}

}
