package edu.berkeley.nlp.assignments.parsing.student;

import java.lang.Integer;
import java.util.*;
import java.util.ArrayList;

import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.assignments.parsing.*;
import edu.berkeley.nlp.ling.Tree;


public class GenerativeParserFactory implements ParserFactory {

  public Parser getParser(List<Tree<String>> trainTrees) {
    return new GenerativeParser(trainTrees);
  }

}

class GenerativeParser implements Parser {
  SimpleLexicon lexicon;
  Grammar grammar;
  Indexer<String> indexer;
  UnaryClosure unaryClosure;
  List<String> currentSentence;
  int numLabels;
  int length;
  boolean TEST = false;

  GenerativeParser(List<Tree<String>> trainTrees) {
    ArrayList<Tree<String>> trees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trainTrees) {
//      if (Trees.PennTreeRenderer.render(tree).contains("Odds")) {
//        Tree newTree = FineAnnotator.annotateTree(tree);
//        trees.add(newTree);
//        System.out.println(Trees.PennTreeRenderer.render(newTree));
//      }
      Tree<String> newTree = FineAnnotator.annotateTree(tree);
      trees.add(newTree);
//      System.out.println(Trees.PennTreeRenderer.render(tree));
//      System.out.println(Trees.PennTreeRenderer.render(newTree));
//      System.exit(0);
    }
    assert trees.size() > 0 : "No training trees";
    lexicon = new SimpleLexicon(trees);
    grammar = Grammar.generativeGrammarFromTrees(trees);
    indexer = grammar.getLabelIndexer();
    unaryClosure = new UnaryClosure(indexer, grammar.getUnaryRules());
    numLabels = indexer.size();

    if (TEST) {
      test();
      System.exit(0);
    }
  }

  double [][][] binaryScores;
  double [][][] unaryScores;
  int [][][] binaryRuleNum;
  int [][][] binaryK;
  UnaryRule [][][] unaryChild;

  void initTables(int size) {
    binaryScores = new double[numLabels][][];
    unaryScores = new double[numLabels][][];
    binaryRuleNum = new int[numLabels][][];
    binaryK = new int[numLabels][][];
    unaryChild = new UnaryRule[numLabels][][];
    initTable(unaryScores, size);
    initTable(binaryScores, size);
    initTable(binaryRuleNum, size);
    initTable(binaryK, size);
    initTable(unaryChild, size);
  }
  
  void initTable(double[][][] table, int size) {
    for (int x = 0; x < numLabels; x++) {
      double [][] page = new double[size][];
      table[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new double[size - i];
        Arrays.fill(page[i], Double.NaN);
      }
    }
  }

  void initTable(int[][][] table, int size) {
    for (int x = 0; x < numLabels; x++) {
      int [][] page = new int[size][];
      table[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new int[size - i];
        Arrays.fill(page[i], -1);
      }
    }
  }

  void initTable(UnaryRule[][][] table, int size) {
    for (int x = 0; x < numLabels; x++) {
      UnaryRule [][] page = new UnaryRule[size][];
      table[x] = page;
      for (int i = 0; i < size; i++) {
        page[i] = new UnaryRule[size - i];
      }
    }
  }

  public Tree<String> getBestParse(List<String> sentence) {
//    System.out.println("sentence = " + sentence);
    currentSentence = sentence;
    length = sentence.size();
    initTables(length);

    for (int x = 0; x < numLabels; x++) {
      String transformedLabel = indexer.get(x);

      double [][] labelTable = binaryScores[x];
      for (int j = 0; j < length; j++) {
        double s = lexicon.scoreTagging(sentence.get(j), transformedLabel);
        if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;
//        if (s != Double.NEGATIVE_INFINITY) {
//          System.out.println(sentence.get(j) + " " + transformedLabel);
//        }
        labelTable[j][length-j-1] = s;
      }
    }

    for (int sum = length - 1; sum >= 0; sum--) {
      for (int i = 0; i <= sum; i++) {
        int j = sum - i;
        double s, ruleScore, max;

        for (int x = 0; x < numLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          if (sum != length - 1) {
            int ruleNum = 0;
            for (BinaryRule rule : grammar.getBinaryRulesByParent(x)) {
              ruleScore = rule.getScore();
              assert length - j > i + 1;
              for (int k = i + 1; k < length - j; k++) {
                s = ruleScore;
                assert ruleScore <= 0;
                s += unaryScores[rule.getLeftChild()][i][length - k];
                s += unaryScores[rule.getRightChild()][k][j];
                if (s > max) {
                  max = s;
                  binaryRuleNum[x][i][j] = ruleNum;
                  binaryK[x][i][j] = k;
                }
              }
              ruleNum++;
            }
            assert max == Double.NEGATIVE_INFINITY || binaryRuleNum[x][i][j] != -1;
            binaryScores[x][i][j] = max;
          }
        }

        for (int x = 0; x < numLabels; x++) {
          max = Double.NEGATIVE_INFINITY;
          boolean selfLooped = false;
          for (UnaryRule rule : unaryClosure.getClosedUnaryRulesByParent(x)) {
            int child = rule.getChild();
            if (child == x) selfLooped = true;
            s = rule.getScore();
            s += binaryScores[child][i][j];
            if (s > max) {
              max = s;
              unaryChild[x][i][j] = rule;
            }
          }
          if (!selfLooped) {
            s = binaryScores[x][i][j];
            if (s > max) {
              max = s;
              unaryChild[x][i][j] = null;
            }
          }
          unaryScores[x][i][j] = max;
        }
      }
    }

//    for (int x = 0; x < numLabels; x++) {
//      System.out.println(x + ": " + indexer.get(x));
//      printArray(unaryScores[x]);
//      printArray(binaryScores[x]);
//    }

//    System.out.println("score = " + unaryScores[0][0][0]);

    Tree<String> ret;
    if (unaryScores[0][0][0] == Double.NEGATIVE_INFINITY) {
      ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
    } else {
      ret = unaryTree(0, 0, 0);
    }
//    System.out.println(Trees.PennTreeRenderer.render(ret));
    return TreeAnnotations.unAnnotateTree(ret);
  }

  Tree<String> unaryTree(int x, int i, int j) {
    UnaryRule rule = unaryChild[x][i][j];
    int child = rule == null ? x : rule.getChild();
    Tree<String> tree;

    if (i + j == length - 1) {
      List<Tree<String>> word = Collections.singletonList(new Tree<String>(currentSentence.get(i)));
      tree = new Tree<String>(indexer.get(child), word);
    } else {
      tree = binaryTree(child, i, j);
    }

    if (child == x) return tree;

    List<Integer> path = unaryClosure.getPath(rule);
    assert path.get(path.size() - 1) == child;
    for (int k = path.size() - 2; k >= 0; k--) {
      int tag = path.get(k);
      tree = new Tree<String>(indexer.get(tag), Collections.singletonList(tree));
    }
    assert path.get(0) == x;
    return tree;
  }

  Tree<String> binaryTree(int x, int i, int j) {
    int ruleNum = binaryRuleNum[x][i][j];
    assert ruleNum != -1 : binaryScores[x][i][j];
    int k = binaryK[x][i][j];
    BinaryRule rule = grammar.getBinaryRulesByParent(x).get(ruleNum);
    ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
    children.add(unaryTree(rule.getLeftChild(), i, length - k));
    children.add(unaryTree(rule.getRightChild(), k, j));
    return new Tree<String>(indexer.get(x), children);
  }

  void test() {
    String raw = "Odds and Ends";
    List<String> sentence = Arrays.asList(raw.split(" "));
    System.out.println(getBestParse(sentence));
  }

  void printArray(double[][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
  void printArray(double[][][] arr) {
    System.out.println(Arrays.deepToString(arr));
  }
}



