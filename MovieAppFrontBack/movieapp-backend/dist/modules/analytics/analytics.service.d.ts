import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
export interface BasicStats {
    totalMovies: number;
    watchedCount: number;
    watchlistCount: number;
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
export interface ActorStats {
    actor: string;
    movieCount: number;
    averageRating?: number;
}
export interface DirectorActorPair {
    director: string;
    actor: string;
    movieCount: number;
}
export interface CountryStats {
    country: string;
    count: number;
    percentage: number;
}
export interface StudioStats {
    studio: string;
    count: number;
}
export interface DivergentOpinion {
    movieTitle: string;
    userRating: number;
    tmdbRating: number;
    difference: number;
}
export interface CompleteAnalytics {
    basicStats: BasicStats;
    watchedStats: {
        genresDistribution: GenreStats[];
        genreCombinations: {
            [key: string]: number;
        };
        decadeDistribution: {
            [key: string]: number;
        };
        topDirectors: DirectorStats[];
        topActors: ActorStats[];
        directorActorPairs: DirectorActorPair[];
        topWriters: any[];
        topComposers: any[];
        topCinematographers: any[];
        productionStudios: StudioStats[];
        productionCountries: CountryStats[];
        continentDistribution: {
            [key: string]: number;
        };
    };
    ratingStats: {
        userRatingsDistribution: {
            [key: string]: number;
        };
        communityRatingsDistribution: {
            [key: string]: number;
        };
        divergentOpinions: DivergentOpinion[];
        avgRatingByDirector: {
            director: string;
            avgRating: number;
        }[];
        avgRatingByActor: {
            actor: string;
            avgRating: number;
        }[];
        avgRatingByGenre: {
            genre: string;
            avgRating: number;
        }[];
        avgRatingByGenreCombination: {
            combination: string;
            avgRating: number;
        }[];
        avgRatingByDecade: {
            [key: string]: number;
        };
        runtimeVsRating: {
            runtime: number;
            rating: number;
        }[];
        revenueVsRating: {
            revenue: number;
            rating: number;
        }[];
    };
}
export declare class AnalyticsService {
    private movieRepository;
    private userMovieRepository;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userMovieRepository: Repository<UserMovieEntity>);
    getCompleteAnalytics(userId: string): Promise<CompleteAnalytics>;
    private generateBasicStats;
    private generateWatchedStats;
    private generateRatingStats;
    getBasicStats(userId: string): Promise<BasicStats>;
    getGenreStats(userId: string): Promise<GenreStats[]>;
    getYearStats(userId: string): Promise<YearStats[]>;
    getDirectorStats(userId: string): Promise<DirectorStats[]>;
    getAdvancedAnalytics(userId: string): Promise<any>;
}
