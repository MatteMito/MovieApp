import { Repository } from 'typeorm';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { MovieEntity } from '../../database/entities/movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';
export interface UserMovieStats {
    totalMovies: number;
    watchedCount: number;
    watchlistCount: number;
    averageRating?: number;
    lastImportDate?: Date;
}
export interface ImportCounters {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
}
export declare class UserMoviesService {
    private userMovieRepository;
    private movieRepository;
    private readonly logger;
    constructor(userMovieRepository: Repository<UserMovieEntity>, movieRepository: Repository<MovieEntity>);
    associateMovieToUser(userId: string, movieId: string, status: MovieStatus, userRating?: number, watchedDate?: Date, userReview?: string): Promise<UserMovieEntity>;
    batchAssociateMoviesToUser(userId: string, movies: Array<{
        movieId: string;
        status: MovieStatus;
        userRating?: number;
        watchedDate?: Date;
        userReview?: string;
    }>): Promise<{
        created: number;
        updated: number;
        watchedInFile: number;
        watchlistInFile: number;
    }>;
    getUserMovies(userId: string, status?: MovieStatus): Promise<Movie[]>;
    getUserMovieStats(userId: string): Promise<UserMovieStats>;
    removeUserMovie(userId: string, movieId: string): Promise<void>;
    updateUserRating(userId: string, movieId: string, rating: number): Promise<UserMovieEntity>;
}
