package librec.undefined;

import happy.coding.math.Stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.data.VectorEntry;
import librec.rating.RegSVD;

public class RBMF extends RegSVD {

	public RBMF(SparseMatrix rm, SparseMatrix tm, int fold) {
		super(rm, tm, fold);

		algoName = "RBMF"; // ranking-based matrix factorization
		isRankingPred = true;
	}

	protected double ranking(int u, int j) {

		double sum = 0, sum_d = 0;
		double theta = 0.05, alpha = 10;
		
		for (int f = 0; f < numFactors; f++) {
			double puf = P.get(u, f);
			double qjf = Q.get(j, f);
			
			if (puf >= theta)
				sum += puf * qjf;
			else if (Math.abs(puf) < theta)
				sum_d += puf * qjf;
		}

		return sum + alpha * sum_d;
	}

	protected Map<Integer, Double> ranking2(int u, Collection<Integer> candItems) {
		SparseVector Ru = trainMatrix.row(u);

		// learn the core features
		List<Integer> fs = new ArrayList<>();
		SparseVector fMeans = new SparseVector(numFactors);
		for (int f = 0; f < numFactors; f++) {
			double[] fvals = new double[Ru.getCount()];
			int i = 0;
			for (VectorEntry ve : Ru) {
				int j = ve.index();
				fvals[i++] = Q.get(j, f);
			}

			double mean = Stats.mean(fvals);
			double std = Stats.sd(fvals);
			if (std < 0.5 || std > 2.5) {
				fs.add(f);
				fMeans.set(f, mean);
			}
		}

		// find items with high core features
		Map<Integer, Double> ranks = new HashMap<>();
		for (int j : candItems) {
			double rank = 0;
			SparseVector jv = new SparseVector(numFactors);
			for (int f : fs) {
				// rank += P.get(u, f) * Q.get(j, f);
				jv.set(f, Q.get(j, f));
			}

			rank = super.correlation(fMeans, jv, "pcc");
			ranks.put(j, rank);
		}

		return ranks;
	}

}
