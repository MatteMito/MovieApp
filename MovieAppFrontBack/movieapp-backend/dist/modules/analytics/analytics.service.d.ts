import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
export interface BasicStats {
    totalMovies: number;
    watchedMovies: number;
    watchlistMovies: number;
    enrichedMovies: number;
    averageRating?: number;
    totalRuntime: number;
    uniqueGenres: number;
    uniqueDirectors: number;
}
export interface GenreStats {
    genre: string;
    count: number;
    percentage: number;
    averageRating?: number;
}
export interface YearStats {
    year: number;
    count: number;
    averageRating?: number;
}
export interface DirectorStats {
    director: string;
    movieCount: number;
    averageRating?: number;
    totalRuntime: number;
}
export interface AdvancedAnalytics {
    basic: BasicStats;
    topGenres: GenreStats[];
    moviesByYear: YearStats[];
    topDirectors: DirectorStats[];
    ratingDistribution: {
        [key: string]: number;
    };
    decadeDistribution: {
        [key: string]: number;
    };
}
export declare class AnalyticsService {
    private movieRepository;
    private userMovieRepository;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userMovieRepository: Repository<UserMovieEntity>);
    getBasicStats(userId: string): Promise<BasicStats>;
    getGenreStats(userId: string): Promise<GenreStats[]>;
    getYearStats(userId: string): Promise<YearStats[]>;
    getDirectorStats(userId: string): Promise<DirectorStats[]>;
    getAdvancedAnalytics(userId: string): Promise<AdvancedAnalytics>;
}
