import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Output {
    String Question;
    Map<String,String> answers;
    Map<String,String> sentences;
    Map<String,String> documents;

    Output (String q, Map<String,String> a,Map<String,String> s,Map<String,String> d){
        this.Question = q;
        this.answers = a;
        this.sentences = s;
        this.documents = d;
    }

}
