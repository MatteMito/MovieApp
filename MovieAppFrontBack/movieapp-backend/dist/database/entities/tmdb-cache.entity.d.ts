export declare class TmdbCacheEntity {
    cache_key: string;
    tmdb_id: number;
    tmdb_data: any;
    title?: string;
    year?: number;
    imdb_id?: string;
    hit_count: number;
    last_accessed_at?: Date;
    expires_at: Date;
    created_at: Date;
    updated_at: Date;
    isExpired(): boolean;
    incrementHit(): void;
    setExpiry(days?: number): void;
}
