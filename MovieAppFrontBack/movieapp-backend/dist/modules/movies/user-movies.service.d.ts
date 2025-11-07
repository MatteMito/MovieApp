import { Repository } from 'typeorm';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';
export interface UserMovieStats {
    userId: string;
    totalMovies: number;
    watchedCount: number;
    watchlistCount: number;
    averageRating: number;
    watched: number;
    watchlist: number;
    total: number;
}
export interface ImportCounters {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
}
export declare class UserMoviesService {
    private readonly userMovieRepository;
    private readonly movieRepository;
    private readonly logger;
    constructor(userMovieRepository: Repository<UserMovieEntity>, movieRepository: Repository<MovieEntity>);
    associateMoviesToUser(userId: string, movies: Movie[], status: 'watched' | 'watchlist'): Promise<void>;
    getUserMovieStats(userId: string): Promise<UserMovieStats>;
    getUserMovies(userId: string, status?: 'watched' | 'watchlist'): Promise<Movie[]>;
    deleteUserMovie(userId: string, movieId: string): Promise<void>;
    deleteAllUserMovies(userId: string): Promise<void>;
}
