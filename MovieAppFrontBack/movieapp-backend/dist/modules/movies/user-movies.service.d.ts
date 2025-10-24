import { Repository } from 'typeorm';
import { UserMovieEntity, MovieStatus } from '../../database/entities/user-movie.entity';
import { Movie } from '../../common/interfaces/movie.interface';
export interface UserMovieStats {
    userId: string;
    totalMovies: number;
    watchedCount: number;
    watchlistCount: number;
    averageRating: number;
}
export interface ImportCounters {
    watchedFromFile: number;
    watchlistFromFile: number;
    totalWatched: number;
    totalWatchlist: number;
}
export declare class UserMoviesService {
    private readonly userMovieRepository;
    private readonly logger;
    constructor(userMovieRepository: Repository<UserMovieEntity>);
    batchAssociateMovies(userId: string, movies: Array<{
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
    getImportCounters(userId: string): Promise<ImportCounters>;
}
