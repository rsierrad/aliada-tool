// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-rdfizer
// Responsible: ALIADA Consortiums
package eu.aliada.rdfizer.pipeline.format.marc.selector;

import static eu.aliada.shared.Strings.isNotNullAndNotEmpty;

import org.marc4j.marc.Record;

/**
 * A composite expression that selects the first not-null evaluation of a set of expressions.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
public class FirstMatch implements Expression<String> {
	private final Expression<String> [] expressions;
	
	/**
	 * Builds a new {@link FirstMatch} with a given expressions chain.
	 * 
	 * @param expressions the expressions that form the execution chain.
	 */
	@SafeVarargs
	public FirstMatch(final Expression<String> ... expressions) {
		this.expressions = expressions;
	}
	
	@Override
	public String evaluate(final Record target) {
		for (final Expression<String> expression : expressions) {
			final String result = expression.evaluate(target);
			if (isNotNullAndNotEmpty(result)) {
				return result;
			}
		}
		return null;
	}
	
	@Override
	public String specs() {
		throw new UnsupportedOperationException();
	}	
}