package com.astersearch.query;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/** One syntax tree, with set evaluation and per-document evaluation. */
public sealed interface Query permits Query.Term, Query.And, Query.Or, Query.Not {
    Set<Integer> evaluate(Function<String, Set<Integer>> lookup, Set<Integer> universe);

    boolean matches(Predicate<String> contains);

    void collectPositiveTerms(Set<String> terms, boolean negated);

    default Set<String> positiveTerms() {
        // Deduplicate terms and keep floating-point summation order stable.
        Set<String> terms = new TreeSet<>();
        collectPositiveTerms(terms, false);
        return terms;
    }

    record Term(String word) implements Query {
        public Set<Integer> evaluate(Function<String, Set<Integer>> lookup, Set<Integer> universe) {
            // Parents mutate candidate sets, never the index's own postings.
            return new HashSet<>(lookup.apply(word));
        }

        public boolean matches(Predicate<String> contains) {
            return contains.test(word);
        }

        public void collectPositiveTerms(Set<String> terms, boolean negated) {
            if (!negated) {
                terms.add(word);
            }
        }
    }

    record And(Query left, Query right) implements Query {
        public Set<Integer> evaluate(Function<String, Set<Integer>> lookup, Set<Integer> universe) {
            Set<Integer> result = left.evaluate(lookup, universe);
            result.retainAll(right.evaluate(lookup, universe));
            return result;
        }

        public boolean matches(Predicate<String> contains) {
            return left.matches(contains) && right.matches(contains);
        }

        public void collectPositiveTerms(Set<String> terms, boolean negated) {
            left.collectPositiveTerms(terms, negated);
            right.collectPositiveTerms(terms, negated);
        }
    }

    record Or(Query left, Query right) implements Query {
        public Set<Integer> evaluate(Function<String, Set<Integer>> lookup, Set<Integer> universe) {
            Set<Integer> result = left.evaluate(lookup, universe);
            result.addAll(right.evaluate(lookup, universe));
            return result;
        }

        public boolean matches(Predicate<String> contains) {
            return left.matches(contains) || right.matches(contains);
        }

        public void collectPositiveTerms(Set<String> terms, boolean negated) {
            left.collectPositiveTerms(terms, negated);
            right.collectPositiveTerms(terms, negated);
        }
    }

    record Not(Query child) implements Query {
        public Set<Integer> evaluate(Function<String, Set<Integer>> lookup, Set<Integer> universe) {
            Set<Integer> result = new HashSet<>(universe);
            result.removeAll(child.evaluate(lookup, universe));
            return result;
        }

        public boolean matches(Predicate<String> contains) {
            return !child.matches(contains);
        }

        public void collectPositiveTerms(Set<String> terms, boolean negated) {
            // Each NOT flips polarity; double negation restores positive terms.
            child.collectPositiveTerms(terms, !negated);
        }
    }
}
