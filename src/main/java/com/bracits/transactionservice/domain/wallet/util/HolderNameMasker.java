package com.bracits.transactionservice.domain.wallet.util;

import com.bracits.transactionservice.domain.constant.DomainConstants;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Masks a holder name for the quote response (FR-01): each word keeps its first and last letter,
 * e.g. "Rahim Uddin" → "R***m U***n". Words of one or two letters keep only the first letter.
 */
public final class HolderNameMasker {

  private static final String WORD_SEPARATOR = " ";
  private static final String WHITESPACE = "\\s+";
  private static final int MIN_LENGTH_KEEPING_LAST = 3;

  private HolderNameMasker() {
  }

  public static String mask(String holderName) {
    if (holderName == null || holderName.isBlank()) {
      return String.valueOf(holderName);
    }

    return Arrays.stream(holderName.trim().split(WHITESPACE))
        .map(HolderNameMasker::maskWord)
        .collect(Collectors.joining(WORD_SEPARATOR));
  }

  private static String maskWord(String word) {
    String mask = String.valueOf(DomainConstants.MASK_CHAR);
    if (word.length() < MIN_LENGTH_KEEPING_LAST) {
      return word.charAt(0) + mask.repeat(word.length() - 1);
    }
    return word.charAt(0) + mask.repeat(word.length() - 2) + word.charAt(word.length() - 1);
  }
}
