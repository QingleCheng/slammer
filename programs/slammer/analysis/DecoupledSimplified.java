/*
 * Originally written by Yong-Woo Lee and Ellen Rathje for the SLAMMER project.
 * This work has been placed into the public domain. You may use this work in
 * any way and for any purpose you wish.
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN THE
 * IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE, ASSUMES
 * _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE USE, MODIFICATION,
 * OR REDISTRIBUTION OF THIS SOFTWARE.
 */

package slammer.analysis;

import java.text.DecimalFormat;

public class DecoupledSimplified extends Analysis
{
	public static String[] BrayAndRathje(final double ky, final double h, final double vs, final double m, final double rock, final double r, final double mheaS, final double meanperS, final double sigdurS, final double normdispS, final double allowdisp, final boolean doScreening)
	{
		String ret[] = new String[13];

		double siteper, nrffact, meanper, dur, tstm, mheamhanrf, kmax, kykmax, normdisp, dispcm, dispin;
		double dur1, dur2, dur3, dur4, dur5;
		double arg;

		siteper = 4.0 * h / vs;
		nrffact = 0.62247 + 0.91958 * Math.exp(-rock / 0.44491);

		if(m <= 7.25)
			meanper = (0.411 + 0.0837 * (m - 6.0) + 0.00208 * r) * Math.exp(meanperS * 0.437);
		else
			meanper = (0.411 + 1.25 * 0.0837 + 0.00208 * r) * Math.exp(meanperS * 0.437);

		dur1 = Math.exp(-0.532 + 0.552 * Math.log((0.95 - 0.05) / (1.0 - 0.95)) - 0.0262 * Math.pow(Math.log((0.95 - 0.05) / (1.0 - 0.95)), 2));
		dur2 = Math.exp(5.204 + 0.851 * (m - 6.0));
		dur3 = Math.pow(10, (1.5 * m + 16.05));
		dur4 = Math.pow(dur2 / dur3, -1.0 / 3.0) / (4900000.0 * 3.2) + 0.063 * (r - 10.0);
		dur5 = Math.pow(dur2 / dur3, -1.0 / 3.0) / (4900000.0 * 3.2);

		if(r >= 10.0)
			dur = Math.exp(Math.log(dur4) + Math.log(dur1) + (0.493 * sigdurS));
		else
			dur = Math.exp(Math.log(dur5) + Math.log(dur1) + (0.493 * sigdurS));

		arg = (siteper / meanper);
		tstm = arg > 8.0 ? 8.0 : arg;

		arg = Math.exp(-0.6244 - 0.7831 * Math.log(tstm) + 0.298 * mheaS);
		mheamhanrf = arg > 1.0 ? 1.0 : arg;

		kmax = mheamhanrf * rock * nrffact;

		kykmax = ky / kmax;

		normdisp = Math.pow(10, 1.87 - 3.477 * kykmax + (normdispS * 0.35));

		dispcm = normdisp * dur * kmax;
		dispin = dispcm / 2.54;

		int incr = 0;
		ret[incr++] = fmtThree.format(siteper);
		ret[incr++] = fmtThree.format(nrffact);
		ret[incr++] = fmtThree.format(meanper);
		ret[incr++] = fmtThree.format(dur);
		ret[incr++] = fmtThree.format(tstm);
		ret[incr++] = fmtThree.format(mheamhanrf);
		ret[incr++] = fmtThree.format(kmax);
		ret[incr++] = fmtThree.format(kykmax);
		ret[incr++] = fmtThree.format(normdisp);
		ret[incr++] = fmtOne.format(dispcm);
		ret[incr++] = fmtOne.format(dispin);

		if(doScreening)
		{
			double medianfreq = nrffact / 3.477 * (1.87 - log10(allowdisp / (rock * nrffact * dur)));
			ret[incr++] = fmtThree.format(medianfreq); // medianfreq
			ret[incr++] = fmtThree.format(medianfreq * rock); // seiscoef
		}
		else
		{
			ret[incr++] = "";
			ret[incr++] = "";
		}

		return ret;
	}
}