package appserver.job.impl;

import appserver.job.Tool;

public class Fibonacci implements Tool {

	@Override
	public Object go(Object parameters) {

		Integer a = 0, b = 1, c, n;

		n = (Integer) parameters;

		if (n == 0)
			return a;
		for (int i = 2; i <= n; i++) {
			c = a + b;
			a = b;
			b = c;
		}

		return b;
	}

}
