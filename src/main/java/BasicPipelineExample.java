import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.mit.jwi.item.*;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.CoreMap;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import org.apache.solr.common.util.Hash;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.assertEquals;


public class BasicPipelineExample {

    static HttpSolrClient solr = null;
    public static void initialize() throws IOException {
        String urlString = "http://localhost:8983/solr/bigboxstore";
        solr = new HttpSolrClient.Builder(urlString).build();
        solr.setParser(new XMLResponseParser());
    }

    public static String text = "Joe Smith was born in California. " +
            "In 2017, he went to Paris, France in the summer. " +
            "His flight left at 3:00pm on July 10th, 2017. " +
            "After eating some escargot for the first time, Joe said, \"That was delicious!\" " +
            "He sent a postcard to his sister Jane Smith. " +
            "After hearing about Joe's trip, Jane decided she might go to France one day. " + "Tim Cook is the CEO of Apple Inc.";

    private static String readFile(String filePath)
    {
        String content = "";

        try
        {
            content = new String ( Files.readAllBytes( Paths.get(filePath) ) );
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return content;
    }

    public static IDictionary init() throws IOException {
        String path = "dict";
        URL url = null;
        try{ url = new URL("file", null, path); }
        catch(MalformedURLException e){ e.printStackTrace(); }
        if(url == null) return null;

        IDictionary dict = new Dictionary(url);
        return dict;

    }

    public static void task1() {
        String filePath = "WikipediaArticles/ExxonMobil.txt";

        //text = readFile( filePath );

        Gson documentGson = new Gson();

        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        //props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp");
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,kbp");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        // props.setProperty("coref.algorithm", "neural");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument document = new CoreDocument(text);
        // annnotate the document
        pipeline.annotate(document);
        // examples

        //documentGson.toJson(document, new FileWriter("document.json"));

        // 10th token of the document
        CoreLabel token = document.tokens().get(10);
        System.out.println("Example: token");
        System.out.println(token);
        System.out.println();

        // text of the first sentence
        String sentenceText = document.sentences().get(1).text();
        System.out.println("Example: sentence");
        System.out.println(sentenceText);
        System.out.println();

        // second sentence
        CoreSentence sentence = document.sentences().get(1);

        // list of the part-of-speech tags for the second sentence
        List<String> posTags = sentence.posTags();
        System.out.println("Example: pos tags");
        System.out.println(posTags);
        System.out.println();

        // list of the ner tags for the second sentence
        List<String> nerTags = sentence.nerTags();
        System.out.println("Example: ner tags");
        System.out.println(nerTags);
        System.out.println();

        // constituency parse for the second sentence
        Tree constituencyParse = sentence.constituencyParse();
        System.out.println("Example: constituency parse");
        System.out.println(constituencyParse);
        System.out.println();

        // dependency parse for the second sentence
        SemanticGraph dependencyParse = sentence.dependencyParse();
        System.out.println("Example: dependency parse");
        System.out.println(dependencyParse);
        System.out.println();


        // kbp relations found in fifth sentence
//        List<RelationTriple> relations =
//                document.sentences().get(4).relations();
//        System.out.println("Example: relation");
//        System.out.println(relations.get(0));
//        System.out.println();

        List<CoreSentence> allSentences = document.sentences();
        //documentGson.toJson(allSentences, new FileWriter("document.json"));
        for(CoreSentence s : allSentences){
            List<RelationTriple> r =  s.relations();
            for(RelationTriple rt : r){
                System.out.println(rt);
            }
        }

    }

    public static void task2(CoreSentence sentence, String docname) throws IOException {
        Set<String> pronouns = new HashSet<>(Arrays.asList("i", "you", "he", "she", "it", "they","me", "you", "him", "her","his", "her", "hers"));
        Set<String> personNer = new TreeSet<>();
        Set<String> orgNer = new TreeSet<>();
        Set<String> locationNer = new TreeSet<>();
        Set<String> dateNer = new TreeSet<>();
        Set<String> timeNer = new TreeSet<>();
        Set<String> relationTriple = new TreeSet<>();
        Set<String> relatedWords = new TreeSet<>();
        IDictionary dict = null;
        dict = init();
        dict.open();


        int i = 0;
        List<CoreLabel> tkns = sentence.tokens();
        for(CoreLabel c : tkns) {
            relatedWords.add(c.lemma());//For every sentence related words are generated (Syns)

            POS tag = getPOSTag(sentence.posTags().get(i++));
            if(tag != null) {
                IIndexWord idxWord = dict.getIndexWord(c.lemma(), tag);
                if (idxWord != null) {
                    for (IWordID wordID : idxWord.getWordIDs()) {
                        IWord iword = dict.getWord(wordID);
                        ISynset synset = iword.getSynset();
                        for (IWord w : synset.getWords()) {
                            relatedWords.add(w.getLemma());
                        }
                    }
                }
            }

        }
        //System.out.println(sentence.tokens());

        // relation triples in each sentence

        List<RelationTriple> r =  sentence.relations();
        for(RelationTriple rt : r){
            relationTriple.add(String.valueOf(rt));
        }

        List<CoreEntityMention> ems = sentence.entityMentions();

        //Entity mentions

        for(CoreEntityMention em : ems){
            if(pronouns.contains(em.text().toLowerCase())){
                continue;
            }
            if(em.entityType().equals("ORGANIZATION")) {
                orgNer.add(em.text());
            } else if(em.entityType().equals("PERSON")) {
                personNer.add(em.text());
            } else if(em.entityType().equals("LOCATION")) {
                locationNer.add(em.text());
            } else if(em.entityType().equals("DATE")) {
                dateNer.add(em.text());
            } else if(em.entityType().equals("TIME")) {
                timeNer.add(em.text());
            }
        }



        SolrInputDocument document1 = new SolrInputDocument();
        document1.addField("docname",docname);
        document1.addField("sentence", sentence.text());
        document1.addField("pos",sentence.posTags());
        document1.addField("dependencyParse",sentence.dependencyParse());
        document1.addField("ner",sentence.nerTags());
        document1.addField("nerMention",sentence.entityMentions());
        document1.addField("person",personNer);
        document1.addField("org",orgNer);
        document1.addField("location",locationNer);
        document1.addField("date",dateNer);
        document1.addField("time",timeNer);
        document1.addField("relationTriple",relationTriple);
        document1.addField("relatedWords",relatedWords);

        System.out.println(relationTriple);


        try {
            solr.add(document1);
            solr.commit();
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static Set<String> getRelatedWords(CoreSentence sentence) throws IOException {
        IDictionary dict = null;
        dict = init();
        dict.open();
        Set<String> relatedWords = new TreeSet<>();
        int i=0;
        List<CoreLabel> tkns = sentence.tokens();
        for(CoreLabel c : tkns) {
            POS tag = getPOSTag(sentence.posTags().get(i++));
            if(tag != null) {
                IIndexWord idxWord = dict.getIndexWord(c.lemma(), tag);
                if (idxWord != null) {
                    for (IWordID wordID : idxWord.getWordIDs()) {
                        IWord iword = dict.getWord(wordID);
                        ISynset synset = iword.getSynset();
                        for (IWord w : synset.getWords()) {
                            relatedWords.add(w.getLemma());
                        }
                    }
                }
            }
        }
        return relatedWords;
    }

    public static POS getPOSTag(String tag){
        if(tag.toLowerCase().startsWith("nn")){
            return POS.NOUN;
        }else if(tag.toLowerCase().startsWith("vb")) {
            return POS.VERB;
        }else if(tag.toLowerCase().startsWith("jj")){
            return  POS.ADJECTIVE;
        } else if(tag.toLowerCase().startsWith("rb")){
            return  POS.ADVERB;
        }
        return null;
    }


    public static CoreDocument parseQuestion(String question){
        Properties props = new Properties();
        // set the list of annotators to run
        //props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp");
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,kbp");
        props.setProperty("ner.applyFineGrained", "false");

        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument questionDocument = new CoreDocument(question);
        // annnotate the document
        pipeline.annotate(questionDocument);


        return questionDocument;
    }

    public static void answer(String question) throws IOException {
        CoreDocument questionDocument = parseQuestion(question);
        List<CoreSentence> questionSentence = questionDocument.sentences();
        List<Output> outputs = new ArrayList<>();
        for(CoreSentence q : questionSentence){
            Set<String> relatedWords = getRelatedWords(q);
            if(q.text().toLowerCase().contains("who")){
                outputs.add(answerWho(questionDocument,q, relatedWords));
            }else if(q.text().toLowerCase().contains("when")){
                outputs.add(answerWhen(questionDocument,q, relatedWords));
            }else if(q.text().toLowerCase().contains("where")){
                outputs.add(answerWhere(questionDocument,q, relatedWords));
            }else{

            }

        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {

            // Java objects to JSON file
            mapper.writeValue(new File("output.json"), outputs);

            // Java objects to JSON string - compact-print
            String jsonString = mapper.writeValueAsString(outputs);

            System.out.println(jsonString);

            // Java objects to JSON string - pretty-print
            String jsonInString2 = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(outputs);

            System.out.println(jsonInString2);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static Output answerWho(CoreDocument question,CoreSentence q, Set<String> relatedWords){
        List<CoreLabel> labelList = new ArrayList<>();
        CoreSentence questionSentence = q;
        SemanticGraph depParse = questionSentence.dependencyParse();

        IndexedWord root = questionSentence.dependencyParse().getFirstRoot();
        Set<IndexedWord> children = depParse.getChildrenWithReln(root,GrammaticalRelation.valueOf("dobj"));
        System.out.println(children.toArray());
        int i=0;
        for(CoreLabel label : questionSentence.tokens()){
            if(getPOSTag(questionSentence.posTags().get(i++)) != null){
                labelList.add(label);
            }
        }
        SolrQuery query = new SolrQuery();
        StringBuilder queryString = new StringBuilder();
        queryString.append("(org:[* TO *] OR person:[* TO *]) AND ( ");
        queryString.append(" ( "+queryBuilder("sentence",labelList)+" )^2 ");
        if(relatedWords.size()!=0){
            queryString.append(" OR " + queryBuilderFromSet("sentence",relatedWords));
        }
        queryString.append(" )");

        query.setQuery(queryString.toString());
        System.out.println(queryString);
        QueryResponse response = null;
        try {
            response = solr.query(query);
        } catch (SolrServerException e) {/* */ } catch (IOException e) {
            e.printStackTrace();
        }
        SolrDocumentList list = response.getResults();
        System.out.println(list.get(0));


        Map<String,String> a = new HashMap<>();
        Map<String,String> s = new HashMap<>();
        Map<String,String> d = new HashMap<>();

        try{
            a.put("1",list.get(0).get("person").toString());
        } catch ( NullPointerException ne){
            if(list.get(0).get("org") != null){
                a.put("1",list.get(0).get("org").toString());
            }else{
                a.put("1","Exact answer not found");
            }
        }
        s.put("1",(String)list.get(0).get("sentence"));
        d.put("1",(String)list.get(0).get("docname"));


        Output output = new Output(questionSentence.text(),a,s,d);
//        Gson documentGson = new GsonBuilder().setPrettyPrinting().create();
//        documentGson.toJson(output);
//        System.out.println(documentGson.toJson(output));



        return output;

    }


    public static Output answerWhen(CoreDocument question,CoreSentence q, Set<String> relatedWords){
        List<CoreLabel> labelList = new ArrayList<>();
        CoreSentence questionSentence = q;
        SemanticGraph depParse = questionSentence.dependencyParse();
        IndexedWord root = questionSentence.dependencyParse().getFirstRoot();
        Set<IndexedWord> children = depParse.getChildrenWithReln(root,GrammaticalRelation.valueOf("dobj"));
        int i=0;
        for(CoreLabel label : questionSentence.tokens()){
            if(getPOSTag(questionSentence.posTags().get(i++)) != null){
                labelList.add(label);
            }
        }
        SolrQuery query = new SolrQuery();
        StringBuilder queryString = new StringBuilder();
        queryString.append("(time:[* TO *] OR date:[* TO *]) AND ( ");
        queryString.append(" ( "+queryBuilder("sentence",labelList)+" )^2 " + " OR " + queryBuilderFromSet("sentence",relatedWords));
        queryString.append(" )");
        if(q.text().toLowerCase().contains("born") || q.text().toLowerCase().contains("birth")){
            queryString.append(" AND relationTriple:*date_of_birth");
        }else if(q.text().toLowerCase().contains("found") || q.text().toLowerCase().contains("form") || q.text().toLowerCase().contains("establish")){
            queryString.append(" AND relationTriple:*date_founded");
        }
        //queryString.append("(sentence:"+questionSentence.dependencyParse().getFirstRoot().lemma());
        //queryString.append(" OR sentence:"+questionSentence.dependencyParse());
        query.setQuery(queryString.toString());
        System.out.println(queryString);
        QueryResponse response = null;
        try {
            response = solr.query(query);
        } catch (SolrServerException e) {/* */ } catch (IOException e) {
            e.printStackTrace();
        }
        SolrDocumentList list = response.getResults();
        System.out.println(list.get(0));

        Map<String,String> a = new HashMap<>();
        Map<String,String> s = new HashMap<>();
        Map<String,String> d = new HashMap<>();

        try{
            a.put("1",list.get(0).get("date").toString());
        } catch ( NullPointerException ne){
            if(list.get(0).get("time") != null){
                a.put("1",list.get(0).get("time").toString());
            }else{
                a.put("1","Exact answer not found");
            }
        }
        s.put("1",(String)list.get(0).get("sentence"));
        d.put("1",(String)list.get(0).get("docname"));


        Output output = new Output(questionSentence.text(),a,s,d);

        return output;

    }

    public static Output answerWhere(CoreDocument question,CoreSentence q, Set<String> relatedWords){
        List<CoreLabel> labelList = new ArrayList<>();
        CoreSentence questionSentence = q;
        SemanticGraph depParse = questionSentence.dependencyParse();
        IndexedWord root = questionSentence.dependencyParse().getFirstRoot();
        Set<IndexedWord> children = depParse.getChildrenWithReln(root,GrammaticalRelation.valueOf("dobj"));
        int i=0;
        for(CoreLabel label : questionSentence.tokens()){
            if(getPOSTag(questionSentence.posTags().get(i++)) != null){
                labelList.add(label);
            }
        }
        SolrQuery query = new SolrQuery();
        StringBuilder queryString = new StringBuilder();
        queryString.append("(location:[* TO *]) AND ( ");
        queryString.append(" ( "+queryBuilder("sentence",labelList)+" )^2 " + " OR " + queryBuilderFromSet("sentence",relatedWords));
        queryString.append(" )");
        //queryString.append("(sentence:"+questionSentence.dependencyParse().getFirstRoot().lemma());
        //queryString.append(" OR sentence:"+questionSentence.dependencyParse());
        query.setQuery(queryString.toString());

        System.out.println(queryString);
        QueryResponse response = null;
        try {
            response = solr.query(query);
        } catch (SolrServerException e) {/* */ } catch (IOException e) {
            e.printStackTrace();
        }
        SolrDocumentList list = response.getResults();
        System.out.println(list.get(0));


        Map<String,String> a = new HashMap<>();
        Map<String,String> s = new HashMap<>();
        Map<String,String> d = new HashMap<>();

        try{
            a.put("1",list.get(0).get("location").toString());
        } catch ( NullPointerException ne){
            a.put("1","Exact answer not found");
        }
        s.put("1",(String)list.get(0).get("sentence"));
        d.put("1",(String)list.get(0).get("docname"));


        Output output = new Output(questionSentence.text(),a,s,d);

        return output;

    }

    public static String queryBuilder(String key, List<CoreLabel> coreLabels) {
        String query = "";
        for(CoreLabel cl: coreLabels) {
            query += key;
            query += ":";
            query += cl.lemma();
            query += " OR ";
        }
        if(!query.equals("")){
            query = query.substring(0,query.lastIndexOf(" OR "));
        }
        return query;
    }

    public static String queryBuilderFromSet(String key, Set<String> coreLabels) {
        String query = "";
        for(String cl: coreLabels) {
            query += key;
            query += ":";
            query += cl;
            query += " OR ";
        }
        if(!query.equals("")){
            query = query.substring(0,query.lastIndexOf(" OR "));
        }
        return query;
    }


    public static void taskTwoHelper(String file) throws IOException {
        String filePath = "WikipediaArticles/"+file;
        text = readFile(filePath);

        Gson documentGson = new Gson();

        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        //props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp");
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,kbp");
        props.setProperty("ner.applyFineGrained", "false");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        //props.setProperty("coref.algorithm", "neural");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument document = new CoreDocument(text);
        // annnotate the document
        pipeline.annotate(document);
        // examples

        //documentGson.toJson(document, new FileWriter("document.json"));

        // second sentence
        //CoreSentence sentence = document.sentences().get(4);
        //task2(sentence);

        List<CoreSentence> allSentences = document.sentences();
        //documentGson.toJson(allSentences, new FileWriter("document.json"));
        for(CoreSentence s : allSentences){
            task2(s,file);
        }
    }
    public static void main(String[] args) throws IOException {
        initialize();
//        File folder = new File("WikipediaArticles");
//
//        String[] files = folder.list();
//
//        for (String file : files)
//        {
//            taskTwoHelper(file);
//        }
        //task1();
        //taskTwoHelper("test");
        String questionText = "";
        if(args.length > 0){
            questionText = readFile(args[0]);
        }else {
            questionText = readFile("question.txt");
        }

        answer(questionText);
    }

}
