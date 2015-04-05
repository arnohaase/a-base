package com.ajjpj.afoundation.collection.immutable;

import com.ajjpj.afoundation.collection.AEquality;

import java.util.Comparator;


/**
 * This class customizes the behavior of a BTree
 *
 * @author arno
 */
public class BTreeSpec {
    public final int maxNumEntries;
    public final int minNumEntries;

    public final Comparator comparator;

    public BTreeSpec (int maxNumEntries, Comparator comparator) {
        this.maxNumEntries = maxNumEntries;
        this.minNumEntries = maxNumEntries / 2;
        this.comparator = comparator;
    }
}
