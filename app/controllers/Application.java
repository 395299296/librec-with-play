package controllers;

import play.*;
import play.cache.Cache;
import play.mvc.*;

import java.util.*;

import manage.RecommendMgr;
import models.*;

public class Application extends Controller {

	/*
	 * Home page.
	 */
    public static void index() {
    	Collections.sort(Movie.allMovies, new Comparator<Movie>() {
            public int compare(Movie m1, Movie m2) {
                return m2.avg_rating.compareTo(m1.avg_rating);
            }
        });
    	List<Movie> movies = new ArrayList<>();
    	String user_id = session.get("user_id");
    	if (user_id == null)
    		movies = RecommendMgr.getInstance().getDefaultItemList();
    	else
    		movies = RecommendMgr.getInstance().getFilterItemList(user_id);
        render("@index", movies);
    }
    
    /*
     * Login page.
     */
    public static void login() {
    	render();
    }
    
    /*
     * Login in by user_id
     */
    public static void is_logged_in(Long user_id) {
    	// Check to see if a user is in the database.
    	User user = User.findUser(user_id);
    	if (user != null) {
	    	session.put("user_id", user_id);
	        flash("message", "Successfully logged in!");
    	} else {
    		flash("message", "Hey! You're user_id is incorrect.");
    	}
    	redirect("/");
	}
    
    /*
     * Logout to an existing account.
     */
    public static void logout() {
    	String user_id = session.get("user_id");
    	if (user_id == null) {
            flash("message", "Hey! You're not even signed in!!!");
    	} else {
            flash("message", "Successfully logged out!");
            session.remove("user_id");
    	}
    	redirect("/");
	}
    
    /*
     * Show list of movies.
     */
    public static void movie_list() {
    	List<Movie> movies = Movie.allMovies; //Movie.findAll();
    	render("@movie_list", movies);
	}

    /*
     * Return page showing the details of a given movie.
     */
    public static void show_movie(Long movie_id) {
    	Movie movie = Movie.getMovie(movie_id);
    	Collections.sort(movie.ratings, new Comparator<UserScore>() {
            public int compare(UserScore m1, UserScore m2) {
                return m1.user.user_id.compareTo(m2.user.user_id);
            }
        });
    	Double totalScore = 0.0;
    	for (UserScore us:movie.ratings) {
    		totalScore += us.score;
    	}
    	// Get average rating of movie
    	Double average = totalScore / movie.ratings.size();
    	User current_user = null;
    	MovieScore user_rating = null;
    	String user_id = session.get("user_id");
    	if (user_id != null) {
    		current_user = User.getUser(Long.parseLong(user_id));
    		user_rating = current_user.getUserRating(movie_id);
    	}
        render("@movie_detail", movie, average, current_user, user_rating);
    }

    /*
     * Show all users with accounts.
     */
    public static void user_list() {
        List<User> users = User.allUsers; //User.findAll();
        render("@user_list", users);
    }
    
    /*
     * Return page showing the details of a given user.
     */
    public static void show_user(Long user_id) {
    	User user = User.getUser(user_id);
        Collections.sort(user.ratings, new Comparator<MovieScore>() {
            public int compare(MovieScore m1, MovieScore m2) {
                return m2.movie.movie_id.compareTo(m2.movie.movie_id);
            }
        });
        render("@user_detail", user);
    }
    
    /*
     * To a particular movie score
     */
    public static void add_new_rating(Long movie_id, Double new_rating) {
    	Long user_id = Long.parseLong(session.get("user_id"));
    	User current_user = User.getUser(user_id);
    	int result = current_user.setUserRating(movie_id, new_rating);
    	switch (result) {
		case 0:
			flash("message", "Your rating was submitted.");
			break;
		case 1:
			flash("message", "Rating added.");
		default:
			break;
		}
    	redirect("/movies/" + movie_id);
    }

}