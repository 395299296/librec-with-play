import play.jobs.*;

import java.io.IOException;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.Table;

import models.Movie;
import models.Rating;
import models.User;
import net.librec.common.LibrecException;
import net.librec.conf.Configuration;
import net.librec.data.convertor.TextDataConvertor;
import net.librec.data.model.ArffInstance;
import net.librec.data.model.TextDataModel;
import net.librec.eval.RecommenderEvaluator;
import net.librec.eval.rating.RMSEEvaluator;
import net.librec.filter.GenericRecommendedFilter;
import net.librec.math.structure.SparseMatrix;
import net.librec.recommender.Recommender;
import net.librec.recommender.RecommenderContext;
import net.librec.recommender.cf.ItemKNNRecommender;
import net.librec.recommender.cf.rating.SVDPlusPlusRecommender;
import net.librec.recommender.item.RecommendedItem;
import net.librec.similarity.*;

@OnApplicationStart
public class Bootstrap extends Job {
    
	private static final Log LOG = LogFactory.getLog(Bootstrap.class);
	
	@Override
    public void doJob() throws LibrecException {  
    	
		long start = 0;
		long end = 0;
		
		// load movie set
		start = System.currentTimeMillis();
		ArrayList<ArffInstance> arffList = null;
		try {
			arffList = Util.readData("data/u.item");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int numRows = arffList.size();
		for (int row = 0; row < numRows; row++) {
            ArffInstance instance = arffList.get(row);
            Movie movie = new Movie(((Double)instance.getValueByAttrName("movie_id")).longValue(), 
            						(String)instance.getValueByAttrName("title"), 
            						(String)instance.getValueByAttrName("released_str"), 
            						(String)instance.getValueByAttrName("imdb_url"));
            // movie.save();
            Movie.allMovies.add(movie);
		}
		end = System.currentTimeMillis();
        LOG.info( "Load movie set costs " + (end - start) + " milliseconds" );
		
		// load user set
        start = System.currentTimeMillis();
		try {
			arffList = Util.readData("data/u.user");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		numRows = arffList.size();
		for (int row = 0; row < numRows; row++) {
            ArffInstance instance = arffList.get(row);
            User user = new User(((Double)instance.getValueByAttrName("user_id")).longValue(), 
            					 ((Double)instance.getValueByAttrName("age")).intValue(), 
            					 (String)instance.getValueByAttrName("gender"), 
            					 (String)instance.getValueByAttrName("profession"), 
            					 (String)instance.getValueByAttrName("zipcode"));
            // user.save();
            User.allUsers.add(user);
		}
		end = System.currentTimeMillis();
        LOG.info( "Load user set costs " + (end - start) + " milliseconds" );
		
		// load rating set
        start = System.currentTimeMillis();
		TextDataConvertor dataConvertor = new TextDataConvertor("data/u.data");
		try {
			dataConvertor.processData();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		SparseMatrix preference = dataConvertor.getPreferenceMatrix();
		Table<Integer, Integer, Double> dataTable = preference.getDataTable();
		BiMap<String, Integer> userIds = dataConvertor.getUserIds();
		BiMap<String, Integer> itemIds = dataConvertor.getItemIds();
		for (Map.Entry<String, Integer> userId : userIds.entrySet()) {
			for (Map.Entry<String, Integer> itemId : itemIds.entrySet()) {
				Object value = dataTable.get(userId.getValue(), itemId.getValue());
				if (value != null) {
					Long user_id = Long.parseLong(userId.getKey());
					Long movie_id = Long.parseLong(itemId.getKey());
					Double score = (Double)value;
					Rating rating = new Rating(user_id, movie_id, score);
					User user = User.getUser(user_id);
					Movie movie = Movie.getMovie(movie_id);
					try {
						user.addMovie(movie, score);
						movie.addUser(user, score);
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
					//rating.save();
					Rating.allRatings.add(rating);
				}
			}
		}
		for (Movie movie:Movie.allMovies) {
			movie.calcAvgRating();
		}
		
		Configuration conf = new Configuration();
        conf.set("dfs.data.dir", "data");
        conf.set("data.input.path", "u.data");
		TextDataModel dataModel = new TextDataModel(conf);
        dataModel.buildDataModel();
        
        // build recommender context
        RecommenderContext context = new RecommenderContext(conf, dataModel);

        // build similarity
        conf.set("rec.recommender.similarity.key" ,"item");
        RecommenderSimilarity similarity = new PCCSimilarity();
        similarity.buildSimilarityMatrix(dataModel);
        context.setSimilarity(similarity);

        // build recommender
        Recommender recommender = new SVDPlusPlusRecommender();
        recommender.setContext(context);

        // run recommender algorithm
        recommender.recommend(context);

        // evaluate the recommended result
        RecommenderEvaluator evaluator = new RMSEEvaluator();
        System.out.println("RMSE:" + recommender.evaluate(evaluator));

        // set id list of filter
        List<String> userIdList = new ArrayList<>();
        List<String> itemIdList = new ArrayList<>();
        userIdList.add("1");
        itemIdList.add("70");

        // filter the recommended result
        List<RecommendedItem> recommendedItemList = recommender.getRecommendedList();
        GenericRecommendedFilter filter = new GenericRecommendedFilter();
        filter.setUserIdList(userIdList);
        filter.setItemIdList(itemIdList);
        recommendedItemList = filter.filter(recommendedItemList);

        // print filter result
        for (RecommendedItem recommendedItem : recommendedItemList) {
            System.out.println(
                    "user:" + recommendedItem.getUserId() + " " +
                    "item:" + recommendedItem.getItemId() + " " +
                    "value:" + recommendedItem.getValue()
            );
        }
        
		end = System.currentTimeMillis();
        LOG.info( "Load rating set costs " + (end - start) + " milliseconds" );
    }
      
}