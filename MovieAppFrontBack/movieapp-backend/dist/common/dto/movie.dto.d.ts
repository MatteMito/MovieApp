export declare class CreateMovieDto {
    title: string;
    year?: number;
    director?: string;
    genres?: string[];
    source: string;
}
export declare class UpdateMovieDto {
    title?: string;
    year?: number;
    director?: string;
    genres?: string[];
}
export declare class EnrichMovieDto {
    id: string;
    title: string;
    year?: number;
    source: string;
}
export declare class BatchUploadDto {
    watchlist: EnrichMovieDto[];
    watched: EnrichMovieDto[];
}
export declare class UserMovieDto {
    movieId: string;
    status: 'watched' | 'watchlist';
    userRating?: number;
    watchedDate?: string;
    userReview?: string;
    source: string;
}
export declare class BatchUploadWithUserDto {
    userId: string;
    watchlist: Array<{
        id: string;
        title: string;
        year?: number;
        source: string;
        userRating?: number;
        userReview?: string;
    }>;
    watched: Array<{
        id: string;
        title: string;
        year?: number;
        source: string;
        userRating?: number;
        watchedDate?: string;
        userReview?: string;
    }>;
}
